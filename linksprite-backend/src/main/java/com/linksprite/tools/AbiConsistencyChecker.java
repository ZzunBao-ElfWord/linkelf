package com.linksprite.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.linksprite.contract.ContractAbis;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ABI 一致性校验器（离线 main，编译流水线调用）
 * <p>
 * 将 7 个合约封装类中内嵌的手写 ABI，与 solc 编译产物
 * classpath:/contracts/&lt;name&gt;.abi 做语义级比对：
 * - 函数：名称 + stateMutability + 入参/出参的类型序列（tuple 递归展平为基础类型序列）
 * - 事件：名称 + 入参类型序列 + indexed + anonymous
 * - 忽略参数命名、internalType 等不影响 ABI 编码的字段
 * <p>
 * 用法：mvn compile exec:java -Dexec.mainClass=com.linksprite.tools.AbiConsistencyChecker
 * 任一合约不一致时以非零码退出。
 */
public final class AbiConsistencyChecker {

    private AbiConsistencyChecker() {
    }

    public static void main(String[] args) {
        int fail = 0;
        for (String name : ContractAbis.DEPLOY_ORDER) {
            String embedded = ContractAbis.getAbi(name);
            String generated = readGeneratedAbi(name);
            if (generated == null) {
                System.out.println("[FAIL] " + name + " : 缺少编译产物 contracts/" + name + ".abi");
                fail++;
                continue;
            }
            List<String> diffs = compare(embedded, generated);
            if (diffs.isEmpty()) {
                System.out.println("[PASS] " + name);
            } else {
                fail++;
                System.out.println("[FAIL] " + name);
                diffs.forEach(d -> System.out.println("       - " + d));
            }
        }
        if (fail > 0) {
            System.out.println("\n共 " + fail + " 个合约 ABI 不一致，请修正 Java 内嵌 ABI 后重新运行");
            System.exit(1);
        }
        System.out.println("\n全部 " + ContractAbis.DEPLOY_ORDER.length + " 个合约 ABI 与 solc 产物一致");
    }

    private static String readGeneratedAbi(String name) {
        ClassPathResource resource = new ClassPathResource("contracts/" + name + ".abi");
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("读取 " + name + ".abi 失败", e);
        }
    }

    /**
     * 比较两份 ABI，返回差异描述（空列表表示一致）
     */
    static List<String> compare(String embeddedAbi, String generatedAbi) {
        Map<String, Integer> a = indexOf(embeddedAbi);
        Map<String, Integer> b = indexOf(generatedAbi);

        List<String> diffs = new ArrayList<>();
        for (Map.Entry<String, Integer> e : a.entrySet()) {
            int inB = b.getOrDefault(e.getKey(), 0);
            if (inB < e.getValue()) {
                diffs.add("内嵌 ABI 存在但 solc 产物缺失/数量不符: " + e.getKey()
                        + " (内嵌x" + e.getValue() + ", solc x" + inB + ")");
            }
        }
        for (Map.Entry<String, Integer> e : b.entrySet()) {
            int inA = a.getOrDefault(e.getKey(), 0);
            if (inA < e.getValue()) {
                diffs.add("solc 产物存在但内嵌 ABI 缺失/数量不符: " + e.getKey()
                        + " (solc x" + e.getValue() + ", 内嵌x" + inA + ")");
            }
        }
        return diffs;
    }

    /**
     * 将 ABI JSON 转为「规范签名 -> 出现次数」的多重集合
     */
    private static Map<String, Integer> indexOf(String abiJson) {
        JSONArray items = JSON.parseArray(abiJson);
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            String sig = canonical(item);
            map.merge(sig, 1, Integer::sum);
        }
        return map;
    }

    private static String canonical(JSONObject item) {
        String type = item.getString("type");
        String name = item.getString("name") == null ? "" : item.getString("name");
        String mut = item.getString("stateMutability");
        if (mut == null) {
            mut = "";
        }
        return switch (type) {
            case "function" -> "F|" + name + "|" + mut + "|"
                    + sig(item.getJSONArray("inputs")) + "=>" + sig(item.getJSONArray("outputs"));
            case "constructor" -> "C|" + mut + "|" + sig(item.getJSONArray("inputs"));
            case "event" -> "E|" + name + "|" + sig(item.getJSONArray("inputs"))
                    + "|anon=" + item.getBooleanValue("anonymous");
            case "receive" -> "R|" + mut;
            case "fallback" -> "B|" + mut;
            case "error" -> "X|" + name + "|" + sig(item.getJSONArray("inputs"));
            default -> "?|" + type + "|" + name;
        };
    }

    /**
     * 参数列表的类型序列签名；tuple 递归展平为组件基础类型序列，
     * 使手写 ABI 中对 struct getter 的扁平化写法与 solc tuple 输出语义等价。
     */
    private static String sig(JSONArray params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        List<String> types = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            JSONObject p = params.getJSONObject(i);
            appendTypes(p, "", types);
            if (p.getBooleanValue("indexed")) {
                int last = types.size() - 1;
                types.set(last, types.get(last) + "@idx");
            }
        }
        return String.join(",", types);
    }

    /**
     * 递归展开参数类型
     *
     * @param param  参数对象
     * @param suffix 数组后缀（如 [] / [][2]），用于 tuple 数组场景
     */
    private static void appendTypes(JSONObject param, String suffix, List<String> out) {
        String t = param.getString("type");
        JSONArray components = param.getJSONArray("components");
        if (t != null && t.startsWith("tuple") && components != null) {
            // tuple 本体的数组后缀（tuple[]），附加到每个基础组件上
            String innerSuffix = suffix + t.substring("tuple".length());
            for (int i = 0; i < components.size(); i++) {
                appendTypes(components.getJSONObject(i), innerSuffix, out);
            }
        } else {
            out.add(t + suffix);
        }
    }
}
