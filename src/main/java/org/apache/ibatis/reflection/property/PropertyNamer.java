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

import org.apache.ibatis.reflection.ReflectionException;

import java.util.Locale;

/**
 * @author Clinton Begin
 */
public final class PropertyNamer {

  private PropertyNamer() {
    // Prevent Instantiation of Static Class
  }

  /**
   * 根据方法名获取属性.
   *
   * @param name
   * @return
   */
  public static String methodToProperty(String name) {
    // is 方法
    if (name.startsWith("is")) {
      name = name.substring(2);
      // get or set
    } else if (name.startsWith("get") || name.startsWith("set")) {
      name = name.substring(3);
    } else {
      // 只处理 is get set
      throw new ReflectionException("Error parsing property name '" + name + "'.  Didn't start with 'is', 'get' or 'set'.");
    }

    // 截取方法获取属性名称
    if (name.length() == 1 || (name.length() > 1 && !Character.isUpperCase(name.charAt(1)))) {
      name = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
    }

    return name;
  }

  /**
   * 根据方法名判断是否是属性
   *
   * @param name
   * @return
   */
  public static boolean isProperty(String name) {
    return isGetter(name) || isSetter(name);
  }

  /**
   * 是否是get方法
   *
   * @param name
   * @return
   */
  public static boolean isGetter(String name) {
    return (name.startsWith("get") && name.length() > 3) || (name.startsWith("is") && name.length() > 2);
  }

  /**
   * 是否是set方法
   *
   * @param name
   * @return
   */
  public static boolean isSetter(String name) {
    return name.startsWith("set") && name.length() > 3;
  }

  public static void main(String[] args) {
    String name = "getAge";
//    String name = "setAge";
//    String name = "isAge";
    System.out.println(methodToProperty(name));
  }
}
