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
package org.apache.ibatis.reflection.wrapper;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectionException;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

import java.util.List;

/**
 * @author Clinton Begin
 */
public class BeanWrapper extends BaseWrapper {

  /**
   * POJO
   */
  private final Object object;
  private final MetaClass metaClass;

  public BeanWrapper(MetaObject metaObject, Object object) {
    super(metaObject);
    this.object = object;
    // pojo 创建metaClass对象
    this.metaClass = MetaClass.forClass(object.getClass(), metaObject.getReflectorFactory());
  }

  @Override
  public Object get(PropertyTokenizer prop) {
    // 判断舒服属于Map Collection
    if (prop.getIndex() != null) {
      Object collection = resolveCollection(prop, object);
      return getCollectionValue(prop, collection);
    } else {
      // 普通属性
      return getBeanProperty(prop, object);
    }
  }

  @Override
  public void set(PropertyTokenizer prop, Object value) {
    // 属于Map Collection
    if (prop.getIndex() != null) {
      Object collection = resolveCollection(prop, object);
      setCollectionValue(prop, collection, value);
    } else {
      // 普通属性
      setBeanProperty(prop, object, value);
    }
  }

  /**
   * 找属性
   */
  @Override
  public String findProperty(String name, boolean useCamelCaseMapping) {
    return metaClass.findProperty(name, useCamelCaseMapping);
  }

  @Override
  public String[] getGetterNames() {
    // 可读get方法名称
    return metaClass.getGetterNames();
  }

  @Override
  public String[] getSetterNames() {
    // 可写set方法名称
    return metaClass.getSetterNames();
  }

  /**
   * 获取set参数CLass
   */
  @Override
  public Class<?> getSetterType(String name) {
    // 分词器
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
      // 为空直接返回
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return metaClass.getSetterType(name);
      } else {
        // 递归调用
        return metaValue.getSetterType(prop.getChildren());
      }
    } else {
      // 直接返回
      return metaClass.getSetterType(name);
    }
  }

  @Override
  public Class<?> getGetterType(String name) {
    // 分词器
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
      // 为空, metaClass 调用直接返回
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return metaClass.getGetterType(name);
      } else {
        // 递归调用
        return metaValue.getGetterType(prop.getChildren());
      }
    } else {
      // 调用返回
      return metaClass.getGetterType(name);
    }
  }

  @Override
  public boolean hasSetter(String name) {
    // 分词器
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 子表达式
    if (prop.hasNext()) {
      // 判断indexedName 是否属于属性
      if (metaClass.hasSetter(prop.getIndexedName())) {
        MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
        if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
          return metaClass.hasSetter(name);
        } else {
          return metaValue.hasSetter(prop.getChildren());
        }
      } else {
        return false;
      }
    } else {
      return metaClass.hasSetter(name);
    }
  }

  @Override
  public boolean hasGetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (metaClass.hasGetter(prop.getIndexedName())) {
        MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
        if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
          return metaClass.hasGetter(name);
        } else {
          return metaValue.hasGetter(prop.getChildren());
        }
      } else {
        return false;
      }
    } else {
      return metaClass.hasGetter(name);
    }
  }

  /**
   * 初始化属性值
   */
  @Override
  public MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory) {
    MetaObject metaValue;
    // 获取set参数Class
    Class<?> type = getSetterType(prop.getName());
    try {
      // 初始化创建值
      Object newObject = objectFactory.create(type);
      metaValue = MetaObject.forObject(newObject, metaObject.getObjectFactory(), metaObject.getObjectWrapperFactory(), metaObject.getReflectorFactory());
      // 设置值
      set(prop, newObject);
    } catch (Exception e) {
      throw new ReflectionException("Cannot set value of property '" + name + "' because '" + name + "' is null and cannot be instantiated on instance of " + type.getName() + ". Cause:" + e.toString(), e);
    }
    return metaValue;
  }

  private Object getBeanProperty(PropertyTokenizer prop, Object object) {
    try {
      // 获取属性getInvoker对象
      Invoker method = metaClass.getGetInvoker(prop.getName());
      try {
        // invoke
        return method.invoke(object, NO_ARGUMENTS);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable t) {
      throw new ReflectionException("Could not get property '" + prop.getName() + "' from " + object.getClass() + ".  Cause: " + t.toString(), t);
    }
  }

  private void setBeanProperty(PropertyTokenizer prop, Object object, Object value) {
    try {
      // set方法参数Class
      Invoker method = metaClass.getSetInvoker(prop.getName());
      Object[] params = {value};
      try {
        // invoke
        method.invoke(object, params);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    } catch (Throwable t) {
      throw new ReflectionException("Could not set property '" + prop.getName() + "' of '" + object.getClass() + "' with value '" + value + "' Cause: " + t.toString(), t);
    }
  }

  @Override
  public boolean isCollection() {
    return false;
  }

  @Override
  public void add(Object element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <E> void addAll(List<E> list) {
    throw new UnsupportedOperationException();
  }

}
