// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.20;

import "./AIAccount.sol";
import "./SpriteComputeToken.sol";

/**
 * @title AIAccountFactory
 * @notice 连接精灵链 - AI 智能账户工厂
 * @dev 批量创建 AI 智能账户，自动注册到 SCT 白名单
 *      每个 AI 智能体创建时自动获得智能账户 + SCT 算力预算
 */
contract AIAccountFactory {
    address public platform;
    address public sctToken;

    // AI 账户地址列表
    address[] public allAIContracts;
    // 控制器 -> AI 账户
    mapping(address => address) public controllerToAccount;

    event AIContractCreated(address indexed aiAccount, address indexed controller);

    modifier onlyPlatform() {
        require(msg.sender == platform, unicode"仅平台可操作");
        _;
    }

    constructor(address _platform, address _sctToken) {
        require(_platform != address(0), unicode"平台地址不能为空");
        require(_sctToken != address(0), unicode"SCT合约不能为空");
        platform = _platform;
        sctToken = _sctToken;
    }

    /**
     * @notice 创建 AI 智能账户
     * @param controller AI 控制器地址
     * @return aiAccount AI 账户合约地址
     */
    function createAIAccount(address controller) public onlyPlatform returns (address aiAccount) {
        require(controller != address(0), unicode"控制器地址不能为空");
        require(controllerToAccount[controller] == address(0), unicode"控制器已有账户");

        // 部署 AI 智能账户合约
        AIAccount account = new AIAccount(platform, controller, sctToken);
        aiAccount = address(account);

        // 注册到 SCT 白名单（让该账户可持有 SCT）
        SpriteComputeToken(sctToken).registerAIContract(aiAccount);

        // 记录
        allAIContracts.push(aiAccount);
        controllerToAccount[controller] = aiAccount;

        emit AIContractCreated(aiAccount, controller);
    }

    /**
     * @notice 批量创建 AI 账户
     */
    function createAIContracts(address[] calldata controllers) external onlyPlatform returns (address[] memory) {
        address[] memory accounts = new address[](controllers.length);
        for (uint256 i = 0; i < controllers.length; i++) {
            accounts[i] = createAIAccount(controllers[i]);
        }
        return accounts;
    }

    /**
     * @notice 查询 AI 账户总数
     */
    function getAIContractCount() external view returns (uint256) {
        return allAIContracts.length;
    }

    /**
     * @notice 获取所有 AI 账户
     */
    function getAllAIContracts() external view returns (address[] memory) {
        return allAIContracts;
    }
}
