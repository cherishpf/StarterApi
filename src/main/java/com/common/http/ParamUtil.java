package com.common.http;

import com.common.util.JsonUtil;
import com.common.util.LogUtil;
import com.common.util.MapUtil;
import com.common.util.StrUtil;

import java.util.HashMap;
import java.util.Map;

public class ParamUtil {
    public static final int MAX_PAGE_SIZE = 50;
    public static final int DEFAULT_PAGE_SIZE = 10;

    private Map<String, Object> paramMap = null;

    public ParamUtil(String paramStr) {
        if (!StrUtil.isEmpty(paramStr)) {
            this.paramMap = JsonUtil.parseObj(paramStr);
        }
    }

    public Map<String, Object> getMap() {
        return paramMap == null ? new HashMap<String, Object>() : new HashMap<>(paramMap);
    }

    public Map<String, Object> getMap(String key) {
        return MapUtil.getMap(paramMap, key);
    }

    public String getStr(String key) {
        return MapUtil.getStr(paramMap, key);
    }

    public Integer getInt(String key) {
        return MapUtil.getInteger(paramMap, key);
    }

    public Long getLong(String key) {
        return MapUtil.getLong(paramMap, key);
    }

    /**
     * Special check for params
     *
     * @return
     */
    public int getPageIndex() {
        Integer v = getInt("pageIndex");
        return v == null || v < 0 ? 0 : v;
    }

    public int getPageSize() {
        Integer v = getInt("pageSize");
        return v == null || v <= 0 || v > MAX_PAGE_SIZE ? DEFAULT_PAGE_SIZE : v;
    }
}
