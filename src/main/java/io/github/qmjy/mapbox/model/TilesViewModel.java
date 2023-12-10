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

import java.io.File;

public class TilesViewModel {
    private final String name;

    /**
     * 构造方法
     * @param file 瓦片文件
     */
    public TilesViewModel(File file) {
        this.name = file.getName();
    }

    /**
     * 获取瓦片文件名称
     * @return 瓦片文件名称
     */
    public String getName() {
        return name;
    }
}
