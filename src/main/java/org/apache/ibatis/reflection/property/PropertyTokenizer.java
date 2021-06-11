/**
 * Copyright 2009-2017 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * 属性分词器解析器
 * 举个例子，在访问 "order[0].item[0].name" 时，
 * 我们希望拆分成 "order[0]"、"item[0]"、"name" 三段，
 * 那么就可以通过 PropertyTokenizer 来实现。
 *
 * @author Clinton Begin
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
  /**
   * 当前属性
   */
  private String name;
  /**
   * 属性索引; 如果存在name 存在index, indexedName = 原始name
   */
  private final String indexedName;
  /**
   * 对于list[0] index=0
   * 对于map[key] index=key
   */
  private String index;
  /**
   * 剩下的子属性
   */
  private final String children;

  /**
   * @param fullname 完整属性; 例: order[0].item[0].name
   */
  public PropertyTokenizer(String fullname) {
    // 根据逗号截取
    int delim = fullname.indexOf('.');
    // 大于-1, 表示有子属性; 根据逗号截取
    if (delim > -1) {
      // 1. name = order[0]
      name = fullname.substring(0, delim);
      // 2. children = item[0].name
      children = fullname.substring(delim + 1);
    } else {
      // 没有子属性, name = fullname
      name = fullname;
      children = null;
    }
    // 记录name; indexedName = order[0]
    indexedName = name;
    // 判断是否属于数组[   截取数组 or map
    delim = name.indexOf('[');
    if (delim > -1) {
      // index = 0
      index = name.substring(delim + 1, name.length() - 1);
      // name = order
      name = name.substring(0, delim);
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  @Override
  public boolean hasNext() {
    return children != null;
  }

  @Override
  public PropertyTokenizer next() {
    // 下一个子属性
    return new PropertyTokenizer(children);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }
}
