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

package io.github.qmjy.mapbox.model;

import org.geotools.api.feature.simple.SimpleFeature;

import java.util.ArrayList;
import java.util.List;

public class AdministrativeDivisionVo implements Cloneable {
    private int id = 0;
    private int parentId = 0;
    private String name = "";
    private String nameEn = "";
    private int adminLevel = 0;
    private List<AdministrativeDivisionVo> children = new ArrayList<>();

    public AdministrativeDivisionVo(SimpleFeature simpleFeature, int parentId) {
        this.id = (int) simpleFeature.getAttribute("osm_id");
        this.name = (String) simpleFeature.getAttribute("local_name");
        Object nameEnObj = simpleFeature.getAttribute("name_en");
        this.nameEn = nameEnObj == null ? "" : String.valueOf(nameEnObj);
        this.parentId = parentId;
        this.adminLevel = (int) simpleFeature.getAttribute("admin_level");
    }

    public int getId() {
        return id;
    }

    public int getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public String getNameEn() {
        return nameEn;
    }

    public int getAdminLevel() {
        return adminLevel;
    }

    public List<AdministrativeDivisionVo> getChildren() {
        return children;
    }

    public void setChildren(List<AdministrativeDivisionVo> children) {
        this.children = children;
    }

    @Override
    public AdministrativeDivisionVo clone() {
        try {
            AdministrativeDivisionVo clone = (AdministrativeDivisionVo) super.clone();
            ArrayList<AdministrativeDivisionVo> administrativeDivisionVos = new ArrayList<>();
            this.getChildren().forEach(item -> {
                administrativeDivisionVos.add(item.clone());
            });
            clone.setChildren(administrativeDivisionVos);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
