// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.20;

import "./AuthorizationRegistry.sol";
import "./TransferPolicy.sol";
import "./KYCRegistry.sol";

/**
 * @title LinkSpriteNFT
 * @notice 连接精灵链 - 统一 NFT 铸造合约
 * @dev 支持 B2B（授权铸造）和 UGC（蚂蚁确权铸造）双来源
 *      采用 ERC-721 标准，铸造后统一流转运营
 */
contract LinkSpriteNFT {
    // NFT 来源类型
    enum SourceType { B2B, UGC }

    // NFT 扩展元数据
    struct NFTExtra {
        SourceType source;       // 来源类型
        uint256 authId;          // B2B: 授权ID（UGC为0）
        string dciId;            // UGC: DCI标识（B2B可为空）
        string antTxHash;        // UGC: 蚂蚁链存证哈希
        string ipfsHash;         // 作品IPFS哈希
        string workHash;         // 作品内容哈希
        uint256 mintTime;        // 铸造时间
        bool paused;             // 是否已暂停流转
    }

    // 平台管理员
    address public platform;
    // 授权管理合约
    AuthorizationRegistry public authRegistry;
    // 流转策略合约
    TransferPolicy public transferPolicy;
    // KYC合约
    KYCRegistry public kycRegistry;

    // ERC-721 基础数据
    string public name = "LinkSprite NFT";
    string public symbol = "LSNFT";
    uint256 public totalSupply;

    // tokenId -> 持有者
    mapping(uint256 => address) private _owners;
    // tokenId -> 元数据
    mapping(uint256 => NFTExtra) public extras;
    // tokenId -> tokenURI
    mapping(uint256 => string) private _tokenURIs;
    // 地址 -> 持有数量
    mapping(address => uint256) private _balances;
    // tokenId -> 授权地址
    mapping(uint256 => address) private _tokenApprovals;
    // 所有者 -> 操作员 -> 是否授权
    mapping(address => mapping(address => bool)) private _operatorApprovals;

    // 事件
    event Transfer(address indexed from, address indexed to, uint256 indexed tokenId);
    event Approval(address indexed owner, address indexed approved, uint256 indexed tokenId);
    event ApprovalForAll(address indexed owner, address indexed operator, bool approved);
    event NFTMinted(
        uint256 indexed tokenId,
        SourceType indexed source,
        address indexed to,
        uint256 authId,
        string dciId
    );
    event NFTPaused(uint256 indexed tokenId, bool paused);

    modifier onlyPlatform() {
        require(msg.sender == platform, unicode"仅平台管理员可操作");
        _;
    }

    modifier tokenExists(uint256 tokenId) {
        require(_owners[tokenId] != address(0), unicode"NFT不存在");
        _;
    }

    constructor(
        address _platform,
        address _authRegistry,
        address _transferPolicy,
        address _kycRegistry
    ) {
        require(_platform != address(0), unicode"平台地址不能为空");
        platform = _platform;
        authRegistry = AuthorizationRegistry(_authRegistry);
        transferPolicy = TransferPolicy(_transferPolicy);
        kycRegistry = KYCRegistry(_kycRegistry);
    }

    // ============================================================
    // 铸造函数
    // ============================================================

    /**
     * @notice B端铸造：必须绑定有效授权
     * @param to 接收地址
     * @param tokenId NFT ID
     * @param tokenURI 元数据URI
     * @param authId 授权ID
     * @param ipfsHash 作品IPFS哈希
     * @param workHash 作品内容哈希
     */
    function mintB2B(
        address to,
        uint256 tokenId,
        string calldata tokenURI,
        uint256 authId,
        string calldata ipfsHash,
        string calldata workHash
    ) external onlyPlatform {
        require(to != address(0), unicode"接收地址不能为空");
        require(_owners[tokenId] == address(0), unicode"tokenId已存在");
        require(bytes(tokenURI).length > 0, unicode"tokenURI不能为空");

        // 校验授权有效性
        require(authRegistry.isValid(authId), unicode"授权无效或已过期");

        _safeMint(to, tokenId);
        _tokenURIs[tokenId] = tokenURI;
        NFTExtra storage eB2B = extras[tokenId];
        eB2B.source = SourceType.B2B;
        eB2B.authId = authId;
        eB2B.ipfsHash = ipfsHash;
        eB2B.workHash = workHash;
        eB2B.mintTime = block.timestamp;

        // 增加授权已铸造计数
        authRegistry.incrementMintedCount(authId);

        emit NFTMinted(tokenId, SourceType.B2B, to, authId, "");
    }

    /**
     * @notice UGC铸造：必须绑定蚂蚁确权证明
     * @param to 接收地址（创作者）
     * @param tokenId NFT ID
     * @param tokenURI 元数据URI
     * @param dciId DCI标识
     * @param antTxHash 蚂蚁链存证哈希
     * @param ipfsHash 作品IPFS哈希
     * @param workHash 作品内容哈希
     */
    function mintUGC(
        address to,
        uint256 tokenId,
        string calldata tokenURI,
        string calldata dciId,
        string calldata antTxHash,
        string calldata ipfsHash,
        string calldata workHash
    ) external onlyPlatform {
        require(to != address(0), unicode"接收地址不能为空");
        require(_owners[tokenId] == address(0), unicode"tokenId已存在");
        require(bytes(dciId).length > 0, unicode"DCI标识不能为空");
        require(bytes(antTxHash).length > 0, unicode"蚂蚁存证哈希不能为空");
        require(kycRegistry.isVerified(to), unicode"创作者未实名认证");

        _safeMint(to, tokenId);
        _tokenURIs[tokenId] = tokenURI;
        NFTExtra storage eUGC = extras[tokenId];
        eUGC.source = SourceType.UGC;
        eUGC.dciId = dciId;
        eUGC.antTxHash = antTxHash;
        eUGC.ipfsHash = ipfsHash;
        eUGC.workHash = workHash;
        eUGC.mintTime = block.timestamp;

        emit NFTMinted(tokenId, SourceType.UGC, to, 0, dciId);
    }

    // ============================================================
    // ERC-721 标准接口
    // ============================================================

    function balanceOf(address owner) external view returns (uint256) {
        require(owner != address(0), unicode"地址不能为空");
        return _balances[owner];
    }

    function ownerOf(uint256 tokenId) external view tokenExists(tokenId) returns (address) {
        return _owners[tokenId];
    }

    function tokenURI(uint256 tokenId) external view tokenExists(tokenId) returns (string memory) {
        return _tokenURIs[tokenId];
    }

    function approve(address to, uint256 tokenId) external tokenExists(tokenId) {
        address owner = _owners[tokenId];
        require(to != owner, unicode"不能授权给自己");
        require(msg.sender == owner || _operatorApprovals[owner][msg.sender], unicode"无权限");
        _tokenApprovals[tokenId] = to;
        emit Approval(owner, to, tokenId);
    }

    function getApproved(uint256 tokenId) external view tokenExists(tokenId) returns (address) {
        return _tokenApprovals[tokenId];
    }

    function setApprovalForAll(address operator, bool approved) external {
        require(operator != msg.sender, unicode"不能授权给自己");
        _operatorApprovals[msg.sender][operator] = approved;
        emit ApprovalForAll(msg.sender, operator, approved);
    }

    function isApprovedForAll(address owner, address operator) external view returns (bool) {
        return _operatorApprovals[owner][operator];
    }

    /**
     * @notice 安全转账（受限转赠，符合国内合规）
     */
    function safeTransferFrom(address from, address to, uint256 tokenId) external {
        safeTransferFrom(from, to, tokenId, "");
    }

    function safeTransferFrom(address from, address to, uint256 tokenId, bytes memory data) public {
        require(_isApprovedOrOwner(msg.sender, tokenId), unicode"无权限");
        _safeTransfer(from, to, tokenId, data);
    }

    function transferFrom(address from, address to, uint256 tokenId) external {
        require(_isApprovedOrOwner(msg.sender, tokenId), unicode"无权限");
        _transfer(from, to, tokenId);
    }

    // ============================================================
    // 内部函数
    // ============================================================

    function _safeMint(address to, uint256 tokenId) internal {
        _owners[tokenId] = to;
        _balances[to]++;
        totalSupply++;
        emit Transfer(address(0), to, tokenId);
    }

    function _safeTransfer(address from, address to, uint256 tokenId, bytes memory data) internal {
        _transfer(from, to, tokenId);
        // 简化：不检查 onERC721Received（FISCO BCOS 联盟链场景）
    }

    function _transfer(address from, address to, uint256 tokenId) internal {
        require(_owners[tokenId] == from, unicode"非所有者");
        require(to != address(0), unicode"接收地址不能为空");
        require(!extras[tokenId].paused, unicode"NFT已暂停流转");

        // 合规校验：实名 + 转赠策略
        require(kycRegistry.isVerified(to), unicode"接收人未实名认证");
        require(transferPolicy.canTransfer(from, to, tokenId), unicode"不符合转赠规则");

        // 清除授权
        delete _tokenApprovals[tokenId];

        _balances[from]--;
        _balances[to]++;
        _owners[tokenId] = to;

        // 记录转赠行为（月频次计数原子生效，失败则整笔回滚）
        transferPolicy.recordTransfer(from);

        emit Transfer(from, to, tokenId);
    }

    function _isApprovedOrOwner(address spender, uint256 tokenId) internal view returns (bool) {
        address owner = _owners[tokenId];
        return spender == owner || _tokenApprovals[tokenId] == spender || _operatorApprovals[owner][spender];
    }

    // ============================================================
    // 管理函数
    // ============================================================

    /**
     * @notice 暂停/恢复NFT流转（紧急情况）
     */
    function setPaused(uint256 tokenId, bool paused) external onlyPlatform tokenExists(tokenId) {
        extras[tokenId].paused = paused;
        emit NFTPaused(tokenId, paused);
    }

    /**
     * @notice 查询NFT权利证明信息
     */
    function getProofInfo(uint256 tokenId) external view tokenExists(tokenId) returns (
        SourceType source,
        string memory proofType,
        string memory proofId
    ) {
        NFTExtra memory e = extras[tokenId];
        if (e.source == SourceType.B2B) {
            return (SourceType.B2B, "AUTHORIZATION", _uint2str(e.authId));
        } else {
            return (SourceType.UGC, "DCI", e.dciId);
        }
    }

    function _uint2str(uint256 _i) internal pure returns (string memory) {
        if (_i == 0) return "0";
        uint256 j = _i;
        uint256 len;
        while (j != 0) { len++; j /= 10; }
        bytes memory bstr = new bytes(len);
        uint256 k = len;
        while (_i != 0) {
            k = k - 1;
            uint8 temp = (48 + uint8(_i - _i / 10 * 10));
            bytes1 b1 = bytes1(temp);
            bstr[k] = b1;
            _i /= 10;
        }
        return string(bstr);
    }
}
