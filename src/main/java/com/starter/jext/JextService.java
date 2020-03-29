package com.starter.jext;

import com.common.util.JsonUtil;
import com.common.util.LogUtil;
import com.common.util.StrUtil;
import com.starter.cache.CacheService;
import com.starter.http.HttpService;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JextService {
    public static final String INFO_KEY = "jext.info";

    @Autowired
    CacheService cacheService;

    @Autowired
    HttpService httpService;

    public Map<String, Object> getInfo(boolean forceUpdateCache) {
        Map<String, Object> infoMap = forceUpdateCache ? null : JsonUtil.parseObj(cacheService.getStr(INFO_KEY));
        if (!MapUtils.isEmpty(infoMap)) {
            return infoMap;
        }

        // 51cto
        String str51CtoCourse = httpService.sendHttpGet("https://edu.51cto.com/center/course/index/search?q=Jext%E6%8A%80%E6%9C%AF%E7%A4%BE%E5%8C%BA");
        String[] course51Cto = StrUtil.parse(str51CtoCourse, "课程：[1-9]\\d*门");
        String[] user51Cto = StrUtil.parse(str51CtoCourse, "学员数量：[1-9]\\d*人");
        String[] userDetail51Cto = StrUtil.parse(str51CtoCourse, "[1-9]\\d*人学习");

        String str51CtoBlog = httpService.sendHttpGet("https://blog.51cto.com/13851865");
        String[] blog51Cto = StrUtil.parse(str51CtoBlog, "<span>[1-9]\\d*(W\\+)*</span>");
        String[] reader51Cto = StrUtil.parse(str51CtoBlog, "阅读&nbsp;[1-9]\\d*(W\\+)*");

        String str51CtoSub = httpService.sendHttpGet("https://blog.51cto.com/cloumn/detail/90");
        String[] sub51Cto = StrUtil.parse(str51CtoSub, "[1-9]\\d*人已订阅");

        // csdn
        String strCsdnCourse = httpService.sendHttpGet("https://edu.csdn.net/lecturer/4306");
        String[] courseCsdn = StrUtil.parse(strCsdnCourse, "在线课程\\([1-9]\\d*\\W*\\)");
        String[] userCsdn = StrUtil.parse(strCsdnCourse, "累计<b>[1-9]\\d*</b>人");
        String[] userDetailCsdn = StrUtil.parse(strCsdnCourse, "<span>[1-9]\\d*人学习过</span>");

        String strCsdnBlog = httpService.sendHttpGet("https://blog.csdn.net/xiziyidi");
        String[] blogCsdn = StrUtil.parse(strCsdnBlog, "<dl class=\"text-center\" title=\"[1-9]\\d*\">");
        String[] readerCsdn = StrUtil.parse(strCsdnBlog, "<span class=\"num\">[1-9]\\d*</span>");
        String[] rankCsdn = StrUtil.parse(strCsdnBlog, "<dl title=\"[1-9]\\d*\">");
        String[] scoreCsdn = StrUtil.parse(strCsdnBlog, "<dd title=\"[1-9]\\d*\">");

        infoMap = new HashMap<String, Object>() {{
            put("cto51", new HashMap<Object, Object>() {{
                put("course", new HashMap<Object, Object>() {{
                    put("count", parseNum(course51Cto));
                    put("userCount", parseNum(user51Cto));
                    put("user", parseNum(userDetail51Cto));
                }});
                put("blog", new HashMap<Object, Object>() {{
                    put("count", parseNum(blog51Cto, "[1-9]\\d*(W\\+)*"));
                    put("reader", parseNum(reader51Cto, "[1-9]\\d*(W\\+)*"));
                    put("subscribe", parseNum(sub51Cto, "[1-9]\\d*"));
                }});
            }});
            put("csdn", new HashMap<Object, Object>() {{
                put("course", new HashMap<Object, Object>() {{
                    put("count", parseNum(courseCsdn));
                    put("userCount", parseNum(userCsdn));
                    put("user", parseNum(userDetailCsdn));
                }});
                put("blog", new HashMap<Object, Object>() {{
                    put("count", parseNum(blogCsdn));
                    put("reader", parseNum(readerCsdn));
                    put("rank", parseNum(rankCsdn));
                    put("score", parseNum(scoreCsdn));
                }});
            }});
        }};

        // Set cache
        cacheService.setStr1Hour(INFO_KEY, JsonUtil.toStr(infoMap));
        return infoMap;
    }

    private Object[] parseNum(String[] strArr) {
        return parseNum(strArr, "[1-9]\\d*");
    }

    private Object[] parseNum(String[] strArr, String regex) {
        String[] numArr = StrUtil.parse(StrUtil.join(strArr, ", "), regex);
        if (ArrayUtils.isEmpty(numArr)) {
            return null;
        }

        List<Object> objList = new ArrayList<>(numArr.length);
        for (String numStr : numArr) {
            try {
                long num = Long.parseLong(numStr);
                objList.add(num);
            } catch (NumberFormatException e) {
                LogUtil.info(e.getMessage());
                objList.add(numStr);
            }
        }
        return objList.toArray();
    }
}
