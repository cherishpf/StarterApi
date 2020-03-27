package com.starter.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.common.enc.Md5Util;
import com.common.file.FileUtil;
import com.common.http.ParamUtil;
import com.common.http.RespEnum;
import com.common.http.RespUtil;
import com.common.util.CodeUtil;
import com.common.util.LogUtil;
import com.common.util.StrUtil;
import com.starter.annotation.AccessLimited;
import com.starter.config.MultipartConfig;
import com.starter.config.ServerConfig;
import com.starter.file.FileHelper;
import com.starter.file.FileTypeEnum;
import com.starter.file.LocationEnum;
import com.starter.file.QiniuService;
import com.starter.service.impl.FileServiceImpl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Api(tags = {"文件管理服务"})
@RestController
@RequestMapping("/file")
public class FileController {
    @Autowired
    MultipartConfig multipartConfig;

    @Autowired
    FileServiceImpl fileService;

    @Autowired
    ServerConfig serverConfig;

    @Autowired
    FileHelper fileHelper;

    @Autowired(required = false)
    QiniuService qiniuService;

    @AccessLimited(count = 1)
    @ApiOperation("上传文件，支持一个或多个同时上传")
    @PostMapping("/upload")
    public Object upload(
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "files", required = false) MultipartFile[] files
    ) {
        if (file != null) {
            LogUtil.info("/file/upload", file.getOriginalFilename());
            return doUpload(FileTypeEnum.File, file, null);
        }

        if (ArrayUtils.isNotEmpty(files)) {
            LogUtil.info("/file/upload", files.length);
            return doUpload(FileTypeEnum.File, files, null);
        }

        return RespUtil.badRequest();
    }

    public Map<String, Object> doUpload(FileTypeEnum type, MultipartFile[] files, String[] specifiedExtArr) {
        // Save files one by one
        List<Map<String, String>> urlList = new ArrayList<Map<String, String>>(files.length);
        for (MultipartFile file : files) {
            Map<String, Object> ret = doUpload(type, file, specifiedExtArr);
            if (RespEnum.OK.getCode() == (int) ret.get("code")) {
                urlList.add(new HashMap<String, String>() {{
                    put("url", (String) ret.get("url"));
                    put("name", (String) ret.get("name"));
                }});
            }
        }

        // Return file names
        Map<String, Object> ret = RespUtil.ok();
        ret.put("files", urlList);
        return ret;
    }

    public Map<String, Object> doUpload(FileTypeEnum type, MultipartFile file, String[] specifiedExtArr) {
        if (file == null || file.isEmpty()) {
            return RespUtil.resp(RespEnum.FILE_EMPTY);
        }

        // Check file extension
        if (!FileHelper.checkFileExt(file, specifiedExtArr)) {
            return RespUtil.resp(RespEnum.UNSUPPORTED_MEDIA_TYPE, StrUtil.join(specifiedExtArr, ", "));
        }

        // Read file
        InputStream fileStream = null;
        try {
            fileStream = file.getInputStream();
        } catch (IOException e) {
            LogUtil.error("Error to get file stream when upload", e.getMessage());
            return RespUtil.resp(RespEnum.ERROR, e.getMessage());
        }

        // Get md5 and check duplicated files
        String md5Str = Md5Util.md5(fileStream);
        com.starter.entity.File fileDb = fileService.getOne(
                new QueryWrapper<com.starter.entity.File>().eq("md5", md5Str)
        );

        if (fileDb != null) {
            LogUtil.info("Existed file in db", file.getOriginalFilename(), md5Str);

            Map<String, Object> ret = RespUtil.ok();
            ret.put("name", fileDb.getName());
            ret.put("url", fileHelper.getFileUrl(fileDb));
            return ret;
        }

        // New file: remember the file name and use md5 as new name to save
        String name = FileUtil.getFileName(file.getOriginalFilename());
        String fileExt = FileUtil.getFileExt(name);
        String fileName = String.format("%s%s%s", type.getFlag(), md5Str, fileExt);

        LocationEnum location = LocationEnum.Service;
        if (qiniuService != null) {
            // upload to cloud service
            location = LocationEnum.Qiniu;
            fileName = qiniuService.upload(fileStream, fileName);
        } else {
            // Save file to local storage
            try {
                byte[] fileBytes = file.getBytes();
                fileHelper.save(fileBytes, fileName);
            } catch (IOException e) {
                return RespUtil.resp(RespEnum.ERROR, e.getMessage());
            }
        }

        // Add file info to db
        String url = fileName;
        int locationId = location.getId();
        fileDb = new com.starter.entity.File() {{
            setName(name);
            setCode(String.format("%s%s", type.getFlag(), CodeUtil.getCode()));
            setMd5(md5Str);
            setUrl(url);
            setSize(file.getSize());
            setFileType(type.getId());
            setLocation(locationId);
        }};
        fileService.save(fileDb);

        Map<String, Object> ret = RespUtil.ok();
        ret.put("name", name);
        ret.put("url", fileHelper.getFileUrl(fileDb));
        return ret;
    }

    @AccessLimited(count = 1)
    @ApiOperation("下载文件")
    @GetMapping("/{name}")
    public Object download(HttpServletResponse response, @PathVariable("name") String name) {
        LogUtil.info("/file", name);
        return doDownload(response, name);
    }

    public Object doDownload(HttpServletResponse response, String name) {
        // Get file info from db
        String md5Str = FileUtil.removeFileExt(name).substring(1);
        com.starter.entity.File fileDb = fileService.getOne(
                new QueryWrapper<com.starter.entity.File>().eq("md5", md5Str)
        );

        if (FileHelper.isValid(fileDb)) {
            // Please download directly from cloud storage service
            if (fileDb.getLocation() != LocationEnum.Service.getId()) {
                LogUtil.info(String.format("Cloud storage file: %s", name));
                return RespUtil.redirect();
            }

            // Set file name
            String fileName = fileDb.getName();
            response.addHeader("Content-Disposition", String.format("attachment;fileName=%s", fileName));
        }

        // Find the saved file
        String filePath = fileHelper.getFilePath(name);
        File file = new File(filePath, name);
        if (!file.exists()) {
            LogUtil.info(String.format("Un existed file: %s", name));
            return RespUtil.resp(RespEnum.NOT_FOUND);
        }

        // Read file
        fileHelper.read(response, file);
        return RespUtil.ok();
    }

    @AccessLimited(count = 1)
    @ApiOperation("文件列表, {pageIndex: 1, pageSize: 2}")
    @PostMapping("/list")
    public Object list(@RequestBody String body) {
        LogUtil.info("/file/list", body);
        return doList(body, null);
    }

    public Object doList(String body, FileTypeEnum[] typeArr) {
        // Parse params
        ParamUtil paramUtil = new ParamUtil(body);
        int size = paramUtil.getPageSize();
        int offset = size * paramUtil.getPageIndex();

        // Set page size and index
        QueryWrapper<com.starter.entity.File> query = new QueryWrapper<com.starter.entity.File>()
                .last(true, String.format("limit %d offset %d", size, offset))
                .select("name", "code", "url", "file_type", "location")
                .orderByDesc("id");

        // Set file type
        if (ArrayUtils.isNotEmpty(typeArr)) {
            List<Integer> typeList = new ArrayList<>();
            for (FileTypeEnum type : typeArr) {
                typeList.add(type.getId());
            }
            query.in("file_type", typeList);
        }

        // Query
        List<com.starter.entity.File> items = fileService.list(query);
        fileHelper.fillInfo(items);

        Map<String, Object> ret = RespUtil.ok();
        ret.put("pageIndex", offset / size);
        ret.put("pageSize", size);
        ret.put("items", items);
        return ret;
    }
}
