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

package io.github.qmjy.mapbox.service;

import io.github.qmjy.mapbox.MapServerDataCenter;
import io.github.qmjy.mapbox.config.AppConfig;
import io.github.qmjy.mapbox.model.*;
import io.github.qmjy.mapbox.util.IOUtils;
import io.github.qmjy.mapbox.util.JdbcUtils;
import no.ecc.vectortile.VectorTileDecoder;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class AsyncService {
    private final Logger logger = LoggerFactory.getLogger(AsyncService.class);
    private final AppConfig appConfig;

    private final MapServerDataCenter mapServerDataCenter;

    /**
     * taskId:完成百分比
     */
    private final Map<String, Integer> mergeTaskProgress = new HashMap<>();

    public AsyncService(AppConfig appConfig, MapServerDataCenter mapServerDataCenter) {
        this.appConfig = appConfig;
        this.mapServerDataCenter = mapServerDataCenter;
    }

    /**
     * 每10秒检查一次
     */
    @Scheduled(fixedRate = 1000)
    public void processFixedRate() {
        //TODO nothing to do yet
    }

    /**
     * 初始化瓦片数据库的POI信息
     */
    @Async("asyncServiceExecutor")
    public void asyncMbtilesToPOI(File tilesetFile) {
        Map<String, String> tileMetaData = mapServerDataCenter.getTileMetaData(tilesetFile.getName());
        if ("pbf".equals(tileMetaData.get("format")) || "mvt".equals(tileMetaData.get("format"))) {

            String idxFilePath = tilesetFile.getAbsolutePath() + ".idx";
            if (!new File(idxFilePath).exists()) {
                JdbcTemplate idxJdbcTemp = JdbcUtils.getInstance().getJdbcTemplate(appConfig.getDriverClassName(), idxFilePath);
                idxJdbcTemp.execute("CREATE TABLE poi(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, geometry TEXT NOT NULL, geometry_type INTEGER NOT NULL);");

                TilesFileModel tilesFileModel = mapServerDataCenter.getTilesFileModel(tilesetFile.getName());
                tilesFileModel.countSize();
                extractPoi2Idx(tilesFileModel, idxJdbcTemp);
                JdbcUtils.getInstance().releaseJdbcTemplate(idxJdbcTemp);
            }
        }
    }

    private void extractPoi2Idx(TilesFileModel tilesFileModel, JdbcTemplate idxJdbcTemp) {
        JdbcTemplate jdbcTemplate = tilesFileModel.getJdbcTemplate();

        int pageSize = 5000;
        List<Poi> cache = new ArrayList<>();

        long totalPage = tilesFileModel.getTilesCount() % pageSize == 0 ? tilesFileModel.getTilesCount() / pageSize : tilesFileModel.getTilesCount() / pageSize + 1;
        for (long currentPage = 0; currentPage < totalPage; currentPage++) {
            List<Map<String, Object>> dataList = jdbcTemplate.queryForList("SELECT * FROM tiles LIMIT " + pageSize + " OFFSET " + currentPage * pageSize);
            for (Map<String, Object> rowDataMap : dataList) {
                byte[] data = (byte[]) rowDataMap.get("tile_data");
                List<Poi> poiList = extractPoi(tilesFileModel.isCompressed() ? IOUtils.decompress(data) : data);
                cache.addAll(poiList);
                if (cache.size() > pageSize) {
                    batchUpdate(idxJdbcTemp, cache);
                    cache.clear();
                }
            }
        }
        batchUpdate(idxJdbcTemp, cache);
    }

    private static void batchUpdate(JdbcTemplate idxJdbcTemp, List<Poi> poiList) {
        idxJdbcTemp.batchUpdate("INSERT INTO poi(name, geometry, geometry_type) VALUES (?, ?, ?)", poiList, poiList.size(), (PreparedStatement ps, Poi poi) -> {
            ps.setString(1, poi.getName());
            ps.setString(2, poi.getGeometry());
            ps.setInt(3, poi.getGeometryType());
        });
    }


    private List<Poi> extractPoi(byte[] data) {
        List<Poi> objects = new ArrayList<>();
        try {
            VectorTileDecoder.FeatureIterable decode = new VectorTileDecoder().decode(data);
            List<VectorTileDecoder.Feature> list = decode.asList();
            for (VectorTileDecoder.Feature feature : list) {
                //TODO 目前就只先保存点类型的数据
                if (feature.getGeometry() instanceof Point) {
                    Poi poi = new Poi(feature);
                    if (poi.getName() != null) {
                        objects.add(poi);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return objects;
    }


    /**
     * 提交文件合并任务。合并任务失败，则process is -1。
     *
     * @param sourceNamePaths 待合并的文件列表
     * @param targetFilePath  目标文件名字
     */
    @Async("asyncServiceExecutor")
    public void submit(String taskId, List<String> sourceNamePaths, String targetFilePath) {
        mergeTaskProgress.put(taskId, 0);
        Optional<MbtileMergeWrapper> wrapperOpt = arrange(sourceNamePaths);

        if (wrapperOpt.isPresent()) {
            MbtileMergeWrapper wrapper = wrapperOpt.get();
            Map<String, MbtileMergeFile> needMerges = wrapper.getNeedMerges();
            long totalCount = wrapper.getTotalCount();
            String largestFilePath = wrapper.getLargestFilePath();

            long completeCount = 0;
            //直接拷贝最大的文件，提升合并速度
            File targetTmpFile = new File(targetFilePath + ".tmp");
            try {
                FileCopyUtils.copy(new File(largestFilePath), targetTmpFile);
                completeCount = needMerges.get(largestFilePath).getCount();
                mergeTaskProgress.put(taskId, (int) (completeCount * 100 / totalCount));
                needMerges.remove(largestFilePath);
            } catch (IOException e) {
                logger.info("Copy the largest file failed: {}", largestFilePath);
                mergeTaskProgress.put(taskId, -1);
                return;
            }

            JdbcTemplate jdbcTemplate = JdbcUtils.getInstance().getJdbcTemplate(appConfig.getDriverClassName(), targetTmpFile.getAbsolutePath());
            Iterator<Map.Entry<String, MbtileMergeFile>> iterator = needMerges.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, MbtileMergeFile> next = iterator.next();
                mergeTo(next.getValue(), jdbcTemplate);
                completeCount += next.getValue().getCount();
                mergeTaskProgress.put(taskId, (int) (completeCount * 100 / totalCount));
                iterator.remove();
                logger.info("Merged file: {}", next.getValue().getFilePath());
            }

            updateMetadata(wrapper, jdbcTemplate);
            JdbcUtils.getInstance().releaseJdbcTemplate(jdbcTemplate);

            if (targetTmpFile.renameTo(new File(targetFilePath))) {
                mergeTaskProgress.put(taskId, 100);
            } else {
                logger.error("Rename file failed: {}", targetFilePath);
            }
        } else {
            mergeTaskProgress.put(taskId, -1);
        }
    }

    private void updateMetadata(MbtileMergeWrapper wrapper, JdbcTemplate jdbcTemplate) {
        String bounds = wrapper.getMinLon() + "," + wrapper.getMinLat() + "," + wrapper.getMaxLon() + "," + wrapper.getMaxLat();
        jdbcTemplate.update("UPDATE metadata SET value = " + wrapper.getMinZoom() + " WHERE name = 'minzoom'");
        jdbcTemplate.update("UPDATE metadata SET value = " + wrapper.getMaxZoom() + " WHERE name = 'maxzoom'");
        jdbcTemplate.update("UPDATE metadata SET value = '" + bounds + "' WHERE name = 'bounds'");
    }

    private Optional<MbtileMergeWrapper> arrange(List<String> sourceNamePaths) {
        MbtileMergeWrapper mbtileMergeWrapper = new MbtileMergeWrapper();

        for (String item : sourceNamePaths) {
            if (mbtileMergeWrapper.getLargestFilePath() == null || mbtileMergeWrapper.getLargestFilePath().isBlank() || new File(item).length() > new File(mbtileMergeWrapper.getLargestFilePath()).length()) {
                mbtileMergeWrapper.setLargestFilePath(item);
            }

            MbtileMergeFile value = wrapModel(item);
            Map<String, String> metadata = value.getMetaMap();

            String format = metadata.get("format");
            if (mbtileMergeWrapper.getFormat().isBlank()) {
                mbtileMergeWrapper.setFormat(format);
            } else {
                //比较mbtiles文件格式是否一致
                if (!mbtileMergeWrapper.getFormat().equals(format)) {
                    logger.error("These Mbtiles files have different formats!");
                    return Optional.empty();
                }
            }

            int minZoom = Integer.parseInt(metadata.get("minzoom"));
            if (mbtileMergeWrapper.getMinZoom() > minZoom) {
                mbtileMergeWrapper.setMinZoom(minZoom);
            }

            int maxZoom = Integer.parseInt(metadata.get("maxzoom"));
            if (mbtileMergeWrapper.getMaxZoom() < maxZoom) {
                mbtileMergeWrapper.setMaxZoom(maxZoom);
            }

            //Such as: 120.85098267,30.68516394,122.03475952,31.87872381
            String[] split = metadata.get("bounds").split(",");

            if (mbtileMergeWrapper.getMinLat() > Double.parseDouble(split[1])) {
                mbtileMergeWrapper.setMinLat(Double.parseDouble(split[1]));
            }
            if (mbtileMergeWrapper.getMaxLat() < Double.parseDouble(split[3])) {
                mbtileMergeWrapper.setMaxLat(Double.parseDouble(split[3]));
            }
            if (mbtileMergeWrapper.getMinLon() > Double.parseDouble(split[0])) {
                mbtileMergeWrapper.setMinLon(Double.parseDouble(split[0]));
            }
            if (mbtileMergeWrapper.getMaxLon() < Double.parseDouble(split[2])) {
                mbtileMergeWrapper.setMaxLon(Double.parseDouble(split[2]));
            }

            mbtileMergeWrapper.addToTotal(value.getCount());
            mbtileMergeWrapper.getNeedMerges().put(item, value);
        }
        return Optional.of(mbtileMergeWrapper);
    }


    public void mergeTo(MbtileMergeFile mbtile, JdbcTemplate jdbcTemplate) {
        int pageSize = 5000;
        long totalPage = mbtile.getCount() % pageSize == 0 ? mbtile.getCount() / pageSize : mbtile.getCount() / pageSize + 1;
        for (long currentPage = 0; currentPage < totalPage; currentPage++) {
            List<Map<String, Object>> dataList = mbtile.getJdbcTemplate().queryForList("SELECT * FROM tiles LIMIT " + pageSize + " OFFSET " + currentPage * pageSize);
            jdbcTemplate.batchUpdate("INSERT INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)", dataList, pageSize, (PreparedStatement ps, Map<String, Object> rowDataMap) -> {
                ps.setInt(1, (int) rowDataMap.get("zoom_level"));
                ps.setInt(2, (int) rowDataMap.get("tile_column"));
                ps.setInt(3, (int) rowDataMap.get("tile_row"));
                ps.setBytes(4, (byte[]) rowDataMap.get("tile_data"));
            });
        }
    }

    private MbtileMergeFile wrapModel(String item) {
        JdbcTemplate jdbcTemplate = JdbcUtils.getInstance().getJdbcTemplate(appConfig.getDriverClassName(), item);
        return new MbtileMergeFile(item, jdbcTemplate);
    }


    public String computeTaskId(List<String> sourceNamePaths) {
        Collections.sort(sourceNamePaths);
        StringBuilder sb = new StringBuilder();
        sourceNamePaths.forEach(sb::append);
        return DigestUtils.md5DigestAsHex(sb.toString().getBytes());
    }

    public Optional<MbtilesOfMergeProgress> getTask(String taskId) {
        if (mergeTaskProgress.containsKey(taskId)) {
            Integer i = mergeTaskProgress.get(taskId);
            return Optional.of(new MbtilesOfMergeProgress(taskId, i));
        } else {
            return Optional.empty();
        }
    }
}
