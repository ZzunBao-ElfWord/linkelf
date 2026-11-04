// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.20;

import "./SpriteComputeToken.sol";

/**
 * @title Paymaster
 * @notice 连接精灵链 - Gas 代付合约（AI 丝滑体验的核心）
 * @dev AI 智能账户用 SCT 支付链上操作费用，Paymaster 用 Native Balance 代付 Gas
 *      AI 全程不接触 Gas，只看到 SCT 消耗
 *
 * 流程：
 * 1. AI 发起操作（如铸造NFT），调用 Paymaster
 * 2. Paymaster 从 AI 账户扣除 SCT（按操作定价）
 * 3. Paymaster 用自有 Native Balance 支付 Gas 执行操作
 * 4. 操作结果返回给 AI
 */
contract Paymaster {
    address public platform;
    SpriteComputeToken public sctToken;

    // 操作定价（SCT）
    mapping(bytes4 => uint256) public operationPrices; // 函数选择器 -> SCT价格
    uint256 public defaultPrice = 10 ether; // 默认 10 SCT（18位精度）

    event GasPaid(address indexed aiAccount, address indexed target, uint256 sctCost, uint256 gasUsed);
    event PriceUpdated(bytes4 indexed selector, uint256 newPrice);

    modifier onlyPlatform() {
        require(msg.sender == platform, unicode"仅平台可操作");
        _;
    }

    constructor(address _platform, address _sctToken) {
        require(_platform != address(0), unicode"平台地址不能为空");
        require(_sctToken != address(0), unicode"SCT合约不能为空");
        platform = _platform;
        sctToken = SpriteComputeToken(_sctToken);
    }

    /**
     * @notice AI 调用此函数执行操作并支付 SCT
     * @param aiAccount AI 智能账户地址
     * @param target 目标合约地址
     * @param data 调用数据
     * @return success 是否成功
     * @return result 返回数据
     */
    function executeForAI(
        address aiAccount,
        address target,
        bytes calldata data
    ) external onlyPlatform returns (bool success, bytes memory result) {
        require(aiAccount != address(0), unicode"AI账户不能为空");
        require(target != address(0), unicode"目标地址不能为空");
        require(sctToken.isAIContract(aiAccount), unicode"非AI账户");

        // 1. 计算操作费用
        bytes4 selector = bytes4(data[:4]);
        uint256 sctCost = operationPrices[selector];
        if (sctCost == 0) {
            sctCost = defaultPrice;
        }

        // 2. 从 AI 账户扣除 SCT（AI 需预先 approve Paymaster）
        uint256 aiBalance = sctToken.balanceOf(aiAccount);
        require(aiBalance >= sctCost, unicode"AI SCT余额不足，需平台充值");

        sctToken.transferFrom(aiAccount, platform, sctCost);

        // 3. 执行操作（Paymaster 代付 Gas）
        uint256 gasBefore = gasleft();
        (success, result) = target.call(data);
        uint256 gasUsed = gasBefore - gasleft();

        emit GasPaid(aiAccount, target, sctCost, gasUsed);
    }

    /**
     * @notice 设置操作定价
     * @param selector 函数选择器
     * @param price SCT 价格（含18位小数）
     */
    function setOperationPrice(bytes4 selector, uint256 price) external onlyPlatform {
        operationPrices[selector] = price;
        emit PriceUpdated(selector, price);
    }

    /**
     * @notice 设置默认价格
     */
    function setDefaultPrice(uint256 price) external onlyPlatform {
        defaultPrice = price;
    }

    /**
     * @notice 充值 Native Balance 到 Paymaster（用于代付 Gas）
     */
    function deposit() external payable onlyPlatform {}

    /**
     * @notice 提取 Native Balance
     */
    function withdraw(uint256 amount) external onlyPlatform {
        (bool success, ) = platform.call{value: amount}("");
        require(success, unicode"提现失败");
    }

    receive() external payable {}
}
