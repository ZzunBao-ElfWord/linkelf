// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.20;

/**
 * @title KYCRegistry
 * @notice 连接精灵链 - 实名认证状态合约
 * @dev 记录用户实名认证状态，NFT铸造和接收前必须通过实名
 *      实名信息由平台审核后上链，不存储敏感个人信息
 */
contract KYCRegistry {
    address public platform;

    // 地址 -> 是否已实名认证
    mapping(address => bool) public verified;
    // 地址 -> 实名时间
    mapping(address => uint256) public verifiedAt;
    // 地址 -> 实名凭证哈希（脱敏后的唯一标识）
    mapping(address => string) public proofHash;

    event Verified(address indexed user, uint256 timestamp);
    event Revoked(address indexed user, uint256 timestamp);

    modifier onlyPlatform() {
        require(msg.sender == platform, unicode"仅平台管理员可操作");
        _;
    }

    constructor(address _platform) {
        require(_platform != address(0), unicode"平台地址不能为空");
        platform = _platform;
    }

    /**
     * @notice 标记用户已实名
     * @param user 用户地址
     * @param _proofHash 实名凭证哈希（脱敏）
     */
    function verify(address user, string calldata _proofHash) external onlyPlatform {
        require(user != address(0), unicode"用户地址不能为空");
        require(!verified[user], unicode"用户已实名");

        verified[user] = true;
        verifiedAt[user] = block.timestamp;
        proofHash[user] = _proofHash;

        emit Verified(user, block.timestamp);
    }

    /**
     * @notice 撤销实名（用户注销等情况）
     */
    function revoke(address user) external onlyPlatform {
        require(verified[user], unicode"用户未实名");
        verified[user] = false;
        emit Revoked(user, block.timestamp);
    }

    /**
     * @notice 校验用户是否已实名
     */
    function isVerified(address user) external view returns (bool) {
        return verified[user];
    }
}
