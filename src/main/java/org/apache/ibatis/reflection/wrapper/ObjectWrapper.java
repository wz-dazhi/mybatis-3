/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection.wrapper;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

import java.util.List;

/**
 * @author Clinton Begin
 */
public interface ObjectWrapper {

  /**
   * 根据属性分词器获取值
   */
  Object get(PropertyTokenizer prop);

  /**
   * 根据属性分词器设置值
   */
  void set(PropertyTokenizer prop, Object value);

  /**
   * 根据名字查找属性
   * @param useCamelCaseMapping 下划线转驼峰
   */
  String findProperty(String name, boolean useCamelCaseMapping);

  /**
   * 获取get属性数组
   */
  String[] getGetterNames();

  /**
   * 获取set属性数组
   */
  String[] getSetterNames();

  /**
   * 获取set方法参数Class
   */
  Class<?> getSetterType(String name);

  /**
   * get方法return Class
   */
  Class<?> getGetterType(String name);

  /**
   * 存在set方法
   */
  boolean hasSetter(String name);

  /**
   * 存在get方法
   */
  boolean hasGetter(String name);

  /**
   * 实例化属性值
   */
  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);

  /**
   * 属于集合
   */
  boolean isCollection();

  /**
   * 集合添加元素
   */
  void add(Object element);

  /**
   * 集合添加元素
   */
  <E> void addAll(List<E> element);

}
