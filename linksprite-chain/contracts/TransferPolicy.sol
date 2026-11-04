// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.20;

/**
 * @title TransferPolicy
 * @notice 连接精灵链 - NFT合规流转策略合约
 * @dev 实现国内数字藏品合规要求：
 *      - 单地址月转赠频次上限（防批量炒作）
 *      - 转赠即刻生效，不设冷却期
 *      - 接收人必须实名认证（由 NFT 合约在转账时校验）
 *      - 禁止二级市场交易
 */
contract TransferPolicy {
    address public platform;
    // 被授权调用 recordTransfer 的 NFT 合约地址
    address public nftContract;

    // 单地址月转赠上限：3次
    uint256 public constant MAX_TRANSFERS_PER_MONTH = 3;

    // 地址 -> 月份 -> 当月转赠次数
    mapping(address => mapping(uint256 => uint256)) public monthlyTransferCount;

    event NftContractBound(address indexed nftContract);

    modifier onlyPlatform() {
        require(msg.sender == platform, unicode"仅平台管理员可操作");
        _;
    }

    modifier onlyPlatformOrNft() {
        require(msg.sender == platform || msg.sender == nftContract, unicode"无权限");
        _;
    }

    constructor(address _platform) {
        require(_platform != address(0), unicode"平台地址不能为空");
        platform = _platform;
    }

    /**
     * @notice 绑定 NFT 合约地址（部署后由平台调用一次）
     * @param _nft LinkSpriteNFT 合约地址
     */
    function setNftContract(address _nft) external onlyPlatform {
        require(_nft != address(0), unicode"NFT合约地址不能为空");
        nftContract = _nft;
        emit NftContractBound(_nft);
    }

    /**
     * @notice 校验是否可以转赠（只读预检，无冷却期，即刻可转）
     * @param from 转出地址
     * @return bool 是否允许转赠
     */
    function canTransfer(address from, address, uint256) external view returns (bool) {
        uint256 month = block.timestamp / 30 days;
        return monthlyTransferCount[from][month] < MAX_TRANSFERS_PER_MONTH;
    }

    /**
     * @notice 记录转赠行为（NFT 合约转账成功后原子调用，或平台手动补偿）
     * @param from 转出地址
     */
    function recordTransfer(address from) external onlyPlatformOrNft {
        uint256 month = block.timestamp / 30 days;
        monthlyTransferCount[from][month]++;
    }

    /**
     * @notice 查询地址转赠状态
     */
    function getTransferStatus(address addr) external view returns (
        uint256 monthlyCount,
        uint256 monthlyLimit
    ) {
        uint256 month = block.timestamp / 30 days;
        return (monthlyTransferCount[addr][month], MAX_TRANSFERS_PER_MONTH);
    }
}
