/*
 * Copyright (c) 2023 QMJY.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.github.qmjy.mapbox.controller;

import io.github.qmjy.mapbox.config.AppConfig;
import io.github.qmjy.mapbox.model.FontsFileModel;
import io.github.qmjy.mapbox.MapServerDataCenter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

/**
 * 支持的字体访问API。
 */
@RestController
@RequestMapping("/api/fonts")
@Tag(name = "Mapbox字体服务管理", description = "Mapbox离线服务接口能力")
public class MapServerFontsController {
    private final Logger logger = LoggerFactory.getLogger(MapServerFontsController.class);
    @Autowired
    private MapServerDataCenter mapServerDataCenter;
    @Autowired
    private AppConfig appConfig;


    /**
     * 返回字体文件二进制流
     *
     * @param fontName 文件名
     * @param range    文件数据区间
     * @return 字体文件的pbf数据
     */
    @GetMapping(value = "/{fontName}/{range}.pbf", produces = "application/x-protobuf")
    @ResponseBody
    public ResponseEntity<ByteArrayResource> loadPbfFont(@PathVariable("fontName") String fontName, @PathVariable("range") String range) {
        Optional<FontsFileModel> fontFolder = mapServerDataCenter.getFontFolder(fontName);
        if (fontFolder.isPresent()) {
            FontsFileModel fontsFileModel = fontFolder.get();
            String fileName = fontsFileModel.getFolder().getAbsolutePath() + File.separator + range + AppConfig.FILE_EXTENSION_NAME_PBF;
            try {
                File file = new File(fileName);
                byte[] buffer = FileCopyUtils.copyToByteArray(file);
                IOUtils.readFully(Files.newInputStream(file.toPath()), buffer);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(AppConfig.APPLICATION_X_PROTOBUF_VALUE);
                ByteArrayResource resource = new ByteArrayResource(buffer);
                return ResponseEntity.ok().headers(headers).contentLength(buffer.length).body(resource);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
}
