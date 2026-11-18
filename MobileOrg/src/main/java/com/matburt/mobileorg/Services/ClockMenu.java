package com.matburt.mobileorg.Services;

/**
 * 长按菜单 clock 项的状态决策（纯逻辑，JUnit 可测）。
 *
 * 菜单项语义随计时状态变化：
 * - 无计时         → CLOCK_IN  （"Clock in"，对该节点开始计时）
 * - 本节点计时中   → CLOCK_OUT （"Clock out"，结束计时写入 LOGBOOK）
 * - 其他节点计时中 → SWITCH    （"切换到此节点计时"，结束旧计时并切换）
 */
public class ClockMenu {

    public enum State { CLOCK_IN, CLOCK_OUT, SWITCH }

    /**
     * @param clockedNodeId 当前正在计时的节点 id；无计时传 -1
     *                      （TimeclockService.getNodeID() 无计时时返回 -1，service 为 null 也传 -1）
     * @param nodeId        长按菜单对应的节点 id
     */
    public static State decide(long clockedNodeId, long nodeId) {
        if (clockedNodeId < 0) return State.CLOCK_IN;
        return clockedNodeId == nodeId ? State.CLOCK_OUT : State.SWITCH;
    }
}
