// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.20;

/**
 * @title AuthorizationRegistry
 * @notice 连接精灵链 - B端IP授权管理合约
 * @dev 记录IP方与平台的授权关系，NFT铸造时必须绑定有效授权
 *      合约层强制：无授权不铸造、超量不铸造、过期不铸造
 */
contract AuthorizationRegistry {
    // 授权范围枚举
    enum AuthScope {
        Exclusive,      // 0 - 独家
        NonExclusive,   // 1 - 非独家
        Sublicensable   // 2 - 可转授权
    }

    // 授权结构体
    struct Authorization {
        address ipOwner;          // IP方链上账户
        string contractNo;        // 授权合同编号
        string workHash;          // 授权作品内容哈希（SHA256）
        uint256 startTime;        // 授权生效时间（Unix时间戳）
        uint256 endTime;          // 授权到期时间（Unix时间戳）
        AuthScope scope;          // 授权范围
        uint256 maxMint;          // 最大铸造数量
        uint256 mintedCount;      // 已铸造数量
        bool revoked;             // 是否已撤销
        address registeredBy;     // 登记人（平台管理员）
        uint256 registerTime;     // 登记时间
    }

    // 平台管理员
    address public platform;
    // NFT 合约地址（铸造成功后由 NFT 合约回调增加计数）
    address public nftContract;
    // 授权ID -> 授权信息
    mapping(uint256 => Authorization) public authorizations;
    // 合同号 -> 授权ID（防重复登记）
    mapping(string => uint256) public contractToAuthId;
    // 授权ID计数
    uint256 public nextAuthId = 1;

    // 事件
    event AuthorizationRegistered(
        uint256 indexed authId,
        string contractNo,
        address indexed ipOwner,
        uint256 maxMint,
        uint256 startTime,
        uint256 endTime
    );
    event AuthorizationRevoked(uint256 indexed authId, address indexed revoker);
    event MintedCountIncremented(uint256 indexed authId, uint256 newCount);
    event NftContractBound(address indexed nftContract);

    modifier onlyPlatform() {
        require(msg.sender == platform, unicode"仅平台管理员可操作");
        _;
    }

    modifier onlyPlatformOrNft() {
        require(msg.sender == platform || msg.sender == nftContract, unicode"仅平台或NFT合约可操作");
        _;
    }

    modifier authExists(uint256 authId) {
        require(authorizations[authId].ipOwner != address(0), unicode"授权不存在");
        _;
    }

    constructor(address _platform) {
        require(_platform != address(0), unicode"平台地址不能为空");
        platform = _platform;
    }

    /**
     * @notice 登记B端IP授权
     * @param ipOwner IP方账户地址
     * @param contractNo 授权合同编号
     * @param workHash 授权作品内容哈希
     * @param startTime 授权生效时间
     * @param endTime 授权到期时间
     * @param scope 授权范围
     * @param maxMint 最大铸造数量
     * @return authId 授权ID
     */
    function registerAuthorization(
        address ipOwner,
        string calldata contractNo,
        string calldata workHash,
        uint256 startTime,
        uint256 endTime,
        AuthScope scope,
        uint256 maxMint
    ) external onlyPlatform returns (uint256 authId) {
        require(ipOwner != address(0), unicode"IP方地址不能为空");
        require(bytes(contractNo).length > 0, unicode"合同编号不能为空");
        require(bytes(workHash).length > 0, unicode"作品哈希不能为空");
        require(endTime > startTime, unicode"到期时间必须晚于生效时间");
        require(maxMint > 0, unicode"最大铸造数量必须大于0");
        require(contractToAuthId[contractNo] == 0, unicode"该合同已登记过授权");

        authId = nextAuthId++;
        authorizations[authId] = Authorization({
            ipOwner: ipOwner,
            contractNo: contractNo,
            workHash: workHash,
            startTime: startTime,
            endTime: endTime,
            scope: scope,
            maxMint: maxMint,
            mintedCount: 0,
            revoked: false,
            registeredBy: msg.sender,
            registerTime: block.timestamp
        });
        contractToAuthId[contractNo] = authId;

        emit AuthorizationRegistered(authId, contractNo, ipOwner, maxMint, startTime, endTime);
    }

    /**
     * @notice 撤销授权（IP方或平台可操作）
     * @param authId 授权ID
     */
    function revokeAuthorization(uint256 authId) external authExists(authId) {
        Authorization storage auth = authorizations[authId];
        require(msg.sender == platform || msg.sender == auth.ipOwner, unicode"仅平台或IP方可撤销");
        require(!auth.revoked, unicode"授权已撤销");

        auth.revoked = true;
        emit AuthorizationRevoked(authId, msg.sender);
    }

    /**
     * @notice 校验授权是否有效（铸造前调用）
     * @param authId 授权ID
     * @return bool 授权是否有效
     */
    function isValid(uint256 authId) external view authExists(authId) returns (bool) {
        Authorization memory auth = authorizations[authId];
        if (auth.revoked) return false;
        if (block.timestamp < auth.startTime) return false;
        if (block.timestamp > auth.endTime) return false;
        if (auth.mintedCount >= auth.maxMint) return false;
        return true;
    }

    /**
     * @notice 增加已铸造计数（NFT合约铸造成功后调用）
     * @param authId 授权ID
     */
    function incrementMintedCount(uint256 authId) external onlyPlatformOrNft authExists(authId) {
        Authorization storage auth = authorizations[authId];
        require(!auth.revoked, unicode"授权已撤销");
        require(auth.mintedCount < auth.maxMint, unicode"已达最大铸造数量");

        auth.mintedCount++;
        emit MintedCountIncremented(authId, auth.mintedCount);
    }

    /**
     * @notice 绑定 NFT 合约地址（部署后由平台调用一次）
     */
    function setNftContract(address _nft) external onlyPlatform {
        require(_nft != address(0), unicode"NFT合约地址不能为空");
        nftContract = _nft;
        emit NftContractBound(_nft);
    }

    /**
     * @notice 查询授权详情
     * @param authId 授权ID
     */
    function getAuthorization(uint256 authId) external view authExists(authId) returns (
        address ipOwner,
        string memory contractNo,
        string memory workHash,
        uint256 startTime,
        uint256 endTime,
        AuthScope scope,
        uint256 maxMint,
        uint256 mintedCount,
        bool revoked
    ) {
        Authorization memory auth = authorizations[authId];
        return (
            auth.ipOwner,
            auth.contractNo,
            auth.workHash,
            auth.startTime,
            auth.endTime,
            auth.scope,
            auth.maxMint,
            auth.mintedCount,
            auth.revoked
        );
    }

    /**
     * @notice 转移平台管理员权限
     */
    function transferPlatform(address newPlatform) external onlyPlatform {
        require(newPlatform != address(0), unicode"新平台地址不能为空");
        platform = newPlatform;
    }
}
