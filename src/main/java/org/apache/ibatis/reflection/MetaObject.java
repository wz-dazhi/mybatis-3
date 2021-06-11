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
package org.apache.ibatis.reflection;

import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.reflection.wrapper.BeanWrapper;
import org.apache.ibatis.reflection.wrapper.CollectionWrapper;
import org.apache.ibatis.reflection.wrapper.MapWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 对象元数据增强
 *
 * @author Clinton Begin
 */
public class MetaObject {

  /**
   * 原始对象
   */
  private final Object originalObject;
  private final ObjectWrapper objectWrapper;
  private final ObjectFactory objectFactory;
  private final ObjectWrapperFactory objectWrapperFactory;
  /**
   * 反射工厂
   */
  private final ReflectorFactory reflectorFactory;

  private MetaObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory) {
    this.originalObject = object;
    this.objectFactory = objectFactory;
    this.objectWrapperFactory = objectWrapperFactory;
    this.reflectorFactory = reflectorFactory;

    // 本身属于对象包装
    if (object instanceof ObjectWrapper) {
      this.objectWrapper = (ObjectWrapper) object;
    } else if (objectWrapperFactory.hasWrapperFor(object)) {
      // 获取对象的包装
      this.objectWrapper = objectWrapperFactory.getWrapperFor(this, object);
    } else if (object instanceof Map) {
      // Map
      this.objectWrapper = new MapWrapper(this, (Map) object);
    } else if (object instanceof Collection) {
      // Collection
      this.objectWrapper = new CollectionWrapper(this, (Collection) object);
    } else {
      // pojo
      this.objectWrapper = new BeanWrapper(this, object);
    }
  }

  public static MetaObject forObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory) {
    if (object == null) {
      return SystemMetaObject.NULL_META_OBJECT;
    } else {
      return new MetaObject(object, objectFactory, objectWrapperFactory, reflectorFactory);
    }
  }

  /**
   * 返回对象工厂
   */
  public ObjectFactory getObjectFactory() {
    return objectFactory;
  }

  public ObjectWrapperFactory getObjectWrapperFactory() {
    return objectWrapperFactory;
  }

  /**
   * 返回反射工厂
   */
  public ReflectorFactory getReflectorFactory() {
    return reflectorFactory;
  }

  /**
   * 原始对象
   */
  public Object getOriginalObject() {
    return originalObject;
  }

  /**
   * 找属性
   *
   * @param propName            属性名称
   * @param useCamelCaseMapping 下划线转驼峰
   * @return
   */
  public String findProperty(String propName, boolean useCamelCaseMapping) {
    return objectWrapper.findProperty(propName, useCamelCaseMapping);
  }

  /**
   * 获取对象的get方法
   */
  public String[] getGetterNames() {
    return objectWrapper.getGetterNames();
  }

  /**
   * 获取对象的set方法
   */
  public String[] getSetterNames() {
    return objectWrapper.getSetterNames();
  }

  /**
   * 根据属性获取set方法参数class
   */
  public Class<?> getSetterType(String name) {
    return objectWrapper.getSetterType(name);
  }

  /**
   * 根据属性获取get方法return class
   */
  public Class<?> getGetterType(String name) {
    return objectWrapper.getGetterType(name);
  }

  /**
   * 是否有set方法
   */
  public boolean hasSetter(String name) {
    return objectWrapper.hasSetter(name);
  }

  /**
   * 是否有get方法
   */
  public boolean hasGetter(String name) {
    return objectWrapper.hasGetter(name);
  }

  /**
   * 获取值
   */
  public Object getValue(String name) {
    // 分词器
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 子表达式
    if (prop.hasNext()) {
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return null;
      } else {
        // 递归调用
        return metaValue.getValue(prop.getChildren());
      }
    } else {
      // 直接获取
      return objectWrapper.get(prop);
    }
  }

  /**
   * 设置值
   */
  public void setValue(String name, Object value) {
    // 分词器
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 子表达式
    if (prop.hasNext()) {
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        if (value == null) {
          // don't instantiate child path if value is null
          return;
        } else {
          // 实例化属性值
          metaValue = objectWrapper.instantiatePropertyValue(name, prop, objectFactory);
        }
      }
      // 递归调用
      metaValue.setValue(prop.getChildren(), value);
    } else {
      // 直接设置
      objectWrapper.set(prop, value);
    }
  }

  /**
   * 获取值的metaObject对象
   */
  public MetaObject metaObjectForProperty(String name) {
    Object value = getValue(name);
    return MetaObject.forObject(value, objectFactory, objectWrapperFactory, reflectorFactory);
  }

  public ObjectWrapper getObjectWrapper() {
    return objectWrapper;
  }

  /**
   * 是否属于集合
   */
  public boolean isCollection() {
    return objectWrapper.isCollection();
  }

  /**
   * 集合添加元素
   */
  public void add(Object element) {
    objectWrapper.add(element);
  }

  /**
   * 集合添加元素
   */
  public <E> void addAll(List<E> list) {
    objectWrapper.addAll(list);
  }

}
