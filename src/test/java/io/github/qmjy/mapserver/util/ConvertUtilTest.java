/*
 * Copyright (c) 2024 QMJY.
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

package io.github.qmjy.mapserver;

import io.github.qmjy.mapserver.util.GeometryUtils;
import lombok.SneakyThrows;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.data.geojson.GeoJSONReader;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.simple.SimpleFeatureImpl;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.filter.identity.FeatureIdImpl;
import org.geotools.geojson.feature.FeatureJSON;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.springframework.boot.json.BasicJsonParser;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 江西省：-913109
 */
public class ConvertUtilTest {

    //系统根行政区划数据
    private static final String ROOT_OSMB_FILE = "W:\\foxgis-server-lite-win\\data\\OSMB\\OSMB-China.geojson";
    //三方系统获取的行政区划边界
    private static final String ORIGIN_FILE = "C:\\Users\\liush\\Desktop\\xingguo.json";
    //最终目标输出
    private static final String TARGET_OSMB_FILE = "C:\\Users\\liush\\Desktop\\OSMB-China-JiangXi.geojson";


    @Test
    public static void convert() throws IOException {
        List<SimpleFeature> originFeatures = getChildrenFromOsmb(-913109);

        String json = Files.readString(Path.of(ORIGIN_FILE));
        List<Object> objects = new BasicJsonParser().parseList(json);
        List<SimpleFeature> features = getSimpleFeatures(objects);

        features.addAll(originFeatures);

        FeatureJSON featureJson = new FeatureJSON();
        StringWriter stringWriter = new StringWriter();

        for (SimpleFeature feature : features) {
            featureJson.writeFeature(feature, stringWriter);
        }
        Files.writeString(Path.of(TARGET_OSMB_FILE), stringWriter.toString());
    }


    private static List<SimpleFeature> getSimpleFeatures(List<Object> objects) {
        int i = 10000;

        SimpleFeatureTypeBuilder ftb = new SimpleFeatureTypeBuilder();
        ftb.setName("ft");
        ftb.add("admin_level", Integer.class);
        ftb.add("osm_id", Integer.class);
        ftb.add("parents", String.class);
        ftb.add("geometry", String.class);
        ftb.add("local_name", String.class);
        ftb.add("name_en", String.class);
        ftb.add("name", String.class);
        ftb.add("boundary", String.class);
        SimpleFeatureType ft = ftb.buildFeatureType();

        List<SimpleFeature> list = new ArrayList<>();
        for (Object object : objects) {
            if (object instanceof LinkedHashMap<?, ?> map) {
                String wkt = getWktString((Map) map.get("areainfo"));
                //TODO: 兴国县admin_level=7 、osm_id=-3180943、parents=-3180943,-3180745,-913109,-270056、geometry=？、local_name=、name_en=、name=
                list.add(new SimpleFeatureImpl(new Object[]{7, i++, "-3180943,-3180745,-913109,-270056", wkt, map.get("areaname"), "", map.get("areaname"), "boundary"}, ft, new FeatureIdImpl(String.valueOf(i++)), false));
            }
        }
        return list;
    }

    private static String getWktString(Map<String, Object> areaInfo) {
        GeometryFactory geometryFactory = new GeometryFactory();
        List<List<List<List<String>>>> list1 = (List<List<List<List<String>>>>) areaInfo.get("coordinates");
        List<List<String>> objects = list1.get(0).get(0);
        Polygon polygon = geometryFactory.createPolygon(getCoordinates(objects));
        Optional<String> s = GeometryUtils.geometry2Wkt(polygon);
        return s.orElse("");
    }

    private static Coordinate[] getCoordinates(List<List<String>> objects) {
        Coordinate[] coordinates = new Coordinate[objects.size()];
        for (int i = 0; i < objects.size(); i++) {
            coordinates[i] = new Coordinate(Double.parseDouble(String.valueOf(objects.get(i).get(0))),
                    Double.parseDouble(String.valueOf(objects.get(i).get(1))));
        }
        return coordinates;
    }


    private static List<SimpleFeature> getChildrenFromOsmb(Integer root) throws IOException {
        List<SimpleFeature> dataList = new ArrayList<>();

        SimpleFeatureIterator features;
        try (GeoJSONReader reader = new GeoJSONReader(new FileInputStream(ROOT_OSMB_FILE))) {
            features = reader.getFeatures().features();
        }
        while (features.hasNext()) {
            SimpleFeature feature = features.next();

            if (feature.getAttribute("parents") != null && feature.getAttribute("parents").toString().contains(String.valueOf(root))) {
                dataList.add(feature);
            }
        }
        return dataList;
    }
}
