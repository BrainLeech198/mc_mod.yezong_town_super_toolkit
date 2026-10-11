package com.yztc.auth.title;

import java.util.ArrayList;
import java.util.List;

/** 一名玩家的称号归属：已获列表 + 当前所选（空串 = 不显示）。 */
public final class TitleRecord {
    public List<String> owned = new ArrayList<>();
    public String selected = "";
}
