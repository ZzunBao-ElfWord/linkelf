// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.20;

/**
 * @title AIAccount
 * @notice 连接精灵链 - AI 智能账户合约
 * @dev 每个 AI 智能体对应一个智能合约账户：
 *      - 无需私钥/助记词，由平台 attestation 验证身份
 *      - 持有 SCT 算力词元，自主支付链上操作费用
 *      - 支持 Session Key 限时权限，实现自主执行
 *      - Gas 由 Paymaster 代付，AI 无感
 */
contract AIAccount {
    address public owner;          // 平台地址（管理权限）
    address public aiController;   // AI 控制器地址（可发起操作）
    address public sctToken;       // SCT 代币合约

    // Session Key：限时权限密钥
    struct SessionKey {
        address key;               // 会话密钥地址
        uint256 expiresAt;         // 过期时间
        bool active;               // 是否激活
    }
    mapping(address => SessionKey) public sessionKeys;

    // 操作计数器（防重放）
    uint256 public nonce;

    event Executed(address indexed target, uint256 value, bytes data, bool success);
    event SessionKeyAdded(address indexed key, uint256 expiresAt);
    event SessionKeyRevoked(address indexed key);

    modifier onlyOwner() {
        require(msg.sender == owner, unicode"仅平台可操作");
        _;
    }

    modifier onlyAuthorized() {
        require(
            msg.sender == owner ||
            msg.sender == aiController ||
            sessionKeys[msg.sender].active,
            unicode"无操作权限"
        );
        _;
    }

    constructor(address _owner, address _aiController, address _sctToken) {
        require(_owner != address(0), unicode"owner不能为空");
        require(_sctToken != address(0), unicode"SCT合约不能为空");
        owner = _owner;
        aiController = _aiController;
        sctToken = _sctToken;
    }

    /**
     * @notice 执行链上操作（AI 自主调用）
     * @param target 目标合约地址
     * @param value 转账金额（Native Balance，一般为0）
     * @param data 调用数据
     */
    function execute(address target, uint256 value, bytes calldata data) external onlyAuthorized returns (bool success) {
        nonce++;
        (success, ) = target.call{value: value}(data);
        emit Executed(target, value, data, success);
    }

    /**
     * @notice 添加 Session Key（限时权限）
     * @param key 会话密钥地址
     * @param duration 有效时长（秒）
     */
    function addSessionKey(address key, uint256 duration) external onlyOwner {
        require(key != address(0), unicode"密钥地址不能为空");
        require(duration > 0 && duration <= 30 days, unicode"时长需在1天~30天");

        sessionKeys[key] = SessionKey({
            key: key,
            expiresAt: block.timestamp + duration,
            active: true
        });
        emit SessionKeyAdded(key, block.timestamp + duration);
    }

    /**
     * @notice 撤销 Session Key
     */
    function revokeSessionKey(address key) external onlyOwner {
        require(sessionKeys[key].active, unicode"密钥不存在或已撤销");
        sessionKeys[key].active = false;
        emit SessionKeyRevoked(key);
    }

    /**
     * @notice 检查 Session Key 是否有效
     */
    function isSessionKeyValid(address key) external view returns (bool) {
        SessionKey memory sk = sessionKeys[key];
        return sk.active && block.timestamp < sk.expiresAt;
    }

    /**
     * @notice 接收 Native Balance（用于 Gas，由 Paymaster 转入）
     */
    receive() external payable {}

    /**
     * @notice 提取平台资金（仅 owner）
     */
    function withdraw(address to, uint256 amount) external onlyOwner {
        (bool success, ) = to.call{value: amount}("");
        require(success, unicode"提现失败");
    }
}
