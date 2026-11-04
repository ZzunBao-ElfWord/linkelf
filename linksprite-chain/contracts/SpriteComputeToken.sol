// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.20;

/**
 * @title SpriteComputeToken (SCT)
 * @notice 连接精灵链 - 精灵算力词元
 * @dev AI 算力资源计量单位（生产资料定性，非金融代币）
 *      受保护转账：仅 AI 智能账户间可流转，人类钱包(EOA)不可接收
 *      不可交易、不可兑换法币、不可进入交易所
 *
 * 合规依据：2026年国内"词元经济"政策 - 词元是新型生产资料而非价值载体
 */
contract SpriteComputeToken {
    string public name = "Sprite Compute Token";
    string public symbol = "SCT";
    uint8 public decimals = 18;
    uint256 public totalSupply;

    address public platform;

    // 余额
    mapping(address => uint256) private _balances;
    // 授权额度
    mapping(address => mapping(address => uint256)) private _allowances;

    // AI 智能账户白名单：只有这些合约地址可以持有和转账 SCT
    mapping(address => bool) public isAIContract;
    // AI 账户工厂（用于注册新 AI 账户）
    address public aiFactory;

    event Transfer(address indexed from, address indexed to, uint256 value);
    event Approval(address indexed owner, address indexed spender, uint256 value);
    event AIContractRegistered(address indexed aiAccount);
    event AIContractRemoved(address indexed aiAccount);
    event Minted(address indexed to, uint256 amount);
    event Burned(address indexed from, uint256 amount);

    modifier onlyPlatform() {
        require(msg.sender == platform, unicode"仅平台管理员可操作");
        _;
    }

    modifier onlyAIOwner(address account) {
        // 只有平台或注册的 AI 工厂可以操作
        require(msg.sender == platform || msg.sender == aiFactory, unicode"无权限");
        _;
    }

    constructor(address _platform) {
        require(_platform != address(0), unicode"平台地址不能为空");
        platform = _platform;
    }

    // ============================================================
    // AI 账户注册管理
    // ============================================================

    /**
     * @notice 注册 AI 智能账户（只有注册后的合约才能持有 SCT）
     * @param aiAccount AI 智能账户合约地址
     */
    function registerAIContract(address aiAccount) external onlyAIOwner(aiAccount) {
        require(aiAccount != address(0), unicode"AI账户地址不能为空");
        require(!isAIContract[aiAccount], unicode"AI账户已注册");
        isAIContract[aiAccount] = true;
        emit AIContractRegistered(aiAccount);
    }

    /**
     * @notice 移除 AI 智能账户
     */
    function removeAIContract(address aiAccount) external onlyPlatform {
        require(isAIContract[aiAccount], unicode"非AI账户");
        isAIContract[aiAccount] = false;
        emit AIContractRemoved(aiAccount);
    }

    /**
     * @notice 设置 AI 账户工厂地址
     */
    function setAIFactory(address factory) external onlyPlatform {
        aiFactory = factory;
    }

    // ============================================================
    // 受保护的转账逻辑（核心：只能 AI 间流转）
    // ============================================================

    function balanceOf(address account) external view returns (uint256) {
        return _balances[account];
    }

    function allowance(address owner, address spender) external view returns (uint256) {
        return _allowances[owner][spender];
    }

    /**
     * @notice 转账（仅 AI 账户间可转，EOA 拒收）
     */
    function transfer(address to, uint256 amount) external returns (bool) {
        _transfer(msg.sender, to, amount);
        return true;
    }

    /**
     * @notice 授权转账（Paymaster 场景）
     */
    function approve(address spender, uint256 amount) external returns (bool) {
        _approve(msg.sender, spender, amount);
        return true;
    }

    /**
     * @notice 从授权地址转账（Paymaster 代付场景）
     */
    function transferFrom(address from, address to, uint256 amount) external returns (bool) {
        uint256 currentAllowance = _allowances[from][msg.sender];
        require(currentAllowance >= amount, unicode"额度不足");
        _transfer(from, to, amount);
        _approve(from, msg.sender, currentAllowance - amount);
        return true;
    }

    function _transfer(address from, address to, uint256 amount) internal {
        require(from != address(0), unicode"转出地址不能为空");
        require(to != address(0), unicode"接收地址不能为空");
        require(_balances[from] >= amount, unicode"余额不足");

        // 核心合规校验：只有 AI 智能账户或平台可以参与 SCT 流转
        // 人类 EOA 钱包无法持有 SCT
        require(
            isAIContract[from] || from == platform,
            unicode"仅AI账户可转出SCT"
        );
        require(
            isAIContract[to] || to == platform,
            unicode"仅AI账户可接收SCT（人类钱包不可持有）"
        );

        _balances[from] -= amount;
        _balances[to] += amount;
        emit Transfer(from, to, amount);
    }

    function _approve(address owner, address spender, uint256 amount) internal {
        require(owner != address(0), unicode"授权人不能为空");
        require(spender != address(0), unicode"被授权人不能为空");
        _allowances[owner][spender] = amount;
        emit Approval(owner, spender, amount);
    }

    // ============================================================
    // 铸造与销毁（按算力资源锚定）
    // ============================================================

    /**
     * @notice 平台铸造 SCT 给 AI 账户（按算力预算分配）
     * @param to AI 账户地址
     * @param amount 数量
     */
    function mintToAI(address to, uint256 amount) external onlyPlatform {
        require(isAIContract[to], unicode"目标必须是AI账户");
        require(amount > 0, unicode"数量必须大于0");

        _balances[to] += amount;
        totalSupply += amount;
        emit Minted(to, amount);
        emit Transfer(address(0), to, amount);
    }

    /**
     * @notice 平台销毁 SCT（AI 服务结算后销毁，控制总量）
     * @param from AI 账户地址
     * @param amount 数量
     */
    function burnFromAI(address from, uint256 amount) external onlyPlatform {
        require(isAIContract[from], unicode"目标必须是AI账户");
        require(_balances[from] >= amount, unicode"余额不足");

        _balances[from] -= amount;
        totalSupply -= amount;
        emit Burned(from, amount);
        emit Transfer(from, address(0), amount);
    }

    // ============================================================
    // 平台间 SCT 回收（AI 服务结算）
    // ============================================================

    /**
     * @notice AI 向平台支付服务费（调用 NFT 铸造等服务时）
     * @param aiAccount AI 账户
     * @param amount 服务费
     */
    function payServiceFee(address aiAccount, uint256 amount) external onlyPlatform {
        require(isAIContract[aiAccount], unicode"必须是AI账户");
        require(_balances[aiAccount] >= amount, unicode"AI余额不足");

        _balances[aiAccount] -= amount;
        _balances[platform] += amount;
        emit Transfer(aiAccount, platform, amount);
    }
}
