package com.yztc.auth.data;

/** 一条高风险操作记录（config/yzt_super/risk.json）。只记动作与主体，绝不含明文密码。 */
public final class Risk {
    public String id;
    public long at;
    /** 操作者（玩家名小写，控制台为 "(console)"）。 */
    public String actor;
    /** 动作词，见 {@code RiskLog} 常量。 */
    public String action;
    /** 目标玩家名（小写），可空。 */
    public String target;
    /** 补充说明（不含明文），可空。 */
    public String detail;
    public boolean handled;
    public String handledBy;
    public long handledAt;
}
