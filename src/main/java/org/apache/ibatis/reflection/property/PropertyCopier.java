/**
 * Copyright 2009-2019 the original author or authors.
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

import org.apache.ibatis.reflection.Reflector;

import java.lang.reflect.Field;

/**
 * 属性copy类
 *
 * @author Clinton Begin
 */
public final class PropertyCopier {

  private PropertyCopier() {
    // Prevent Instantiation of Static Class
  }

  /**
   * sourceBean对象属性复制到destinationBean
   *
   * @param type            Class对象
   * @param sourceBean      原bean
   * @param destinationBean 目标bean
   */
  public static void copyBeanProperties(Class<?> type, Object sourceBean, Object destinationBean) {
    Class<?> parent = type;
    // 向上递归查找父类
    while (parent != null) {
      // 获取所有的属性
      final Field[] fields = parent.getDeclaredFields();
      for (Field field : fields) {
        try {
          try {
            // 将sourceBean对象的字段赋值到destinationBean对象的字段
            field.set(destinationBean, field.get(sourceBean));
          } catch (IllegalAccessException e) {
            // 设置私有可访问
            if (Reflector.canControlMemberAccessible()) {
              field.setAccessible(true);
              field.set(destinationBean, field.get(sourceBean));
            } else {
              throw e;
            }
          }
        } catch (Exception e) {
          // Nothing useful to do, will only fail on final fields, which will be ignored.
        }
      }
      // 获取父类
      parent = parent.getSuperclass();
    }
  }

}
