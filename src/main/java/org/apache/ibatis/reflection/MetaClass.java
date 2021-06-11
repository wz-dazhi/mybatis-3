/**
 * Copyright 2009-2020 the original author or authors.
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

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

/**
 * @author Clinton Begin
 */
public class MetaClass {
  /**
   * 反射器工厂
   */
  private final ReflectorFactory reflectorFactory;
  /**
   * 反射器
   */
  private final Reflector reflector;

  /**
   * 私有构造方法
   */
  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    this.reflector = reflectorFactory.findForClass(type);
  }

  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

  /**
   * 返回属性get方法return的 MetaClass对象
   *
   * @param name 属性
   * @return
   */
  public MetaClass metaClassForProperty(String name) {
    // 获取get方法return的Class对象
    Class<?> propType = reflector.getGetterType(name);
    // 返回MetaClass对象
    return MetaClass.forClass(propType, reflectorFactory);
  }

  public String findProperty(String name) {
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  /**
   * 找属性
   */
  public String findProperty(String name, boolean useCamelCaseMapping) {
    // 下划线转驼峰, 逻辑在下面
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    return findProperty(name);
  }

  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  /**
   * 获取属性set方法参数Class对象
   */
  public Class<?> getSetterType(String name) {
    // 分词器
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 子表达式
    if (prop.hasNext()) {
      // 递归调用
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
      // 无子表达式
      return reflector.getSetterType(prop.getName());
    }
  }

  /**
   * get方法return的Class对象
   */
  public Class<?> getGetterType(String name) {
    // 分词器
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 子表达式, 递归调用
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    // 无子表达式, 判断是否Collection的子类
    return getGetterType(prop);
  }

  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  private Class<?> getGetterType(PropertyTokenizer prop) {
    // get方法return的Class
    Class<?> type = reflector.getGetterType(prop.getName());
    // 索引不为空, 并且是Collection集合的子类
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      // 获取返回类型
      Type returnType = getGenericGetterType(prop.getName());
      // 返回类型属于泛型
      if (returnType instanceof ParameterizedType) {
        // 获取实际类型, 并且只有一个(Collection<E>), 只有一个E
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          returnType = actualTypeArguments[0];
          // 是class直接赋予type
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
          } else if (returnType instanceof ParameterizedType) {
            // 泛型, 获取真实type
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }

  private Type getGenericGetterType(String propertyName) {
    try {
      // 获取get方法Invoer对象
      Invoker invoker = reflector.getGetInvoker(propertyName);
      // 方法Invoker
      if (invoker instanceof MethodInvoker) {
        // 反射获取method字段
        Field declaredMethod = MethodInvoker.class.getDeclaredField("method");
        declaredMethod.setAccessible(true);
        // 获取method属性
        Method method = (Method) declaredMethod.get(invoker);
        // 解析method属性的返回类型
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());
      } else if (invoker instanceof GetFieldInvoker) {
        // 属性FieldInvoker
        // 反射获取field字段
        Field declaredField = GetFieldInvoker.class.getDeclaredField("field");
        declaredField.setAccessible(true);
        // 获取field属性
        Field field = (Field) declaredField.get(invoker);
        // 获取字段的类型
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException | IllegalAccessException e) {
      // Ignored
    }
    return null;
  }

  /**
   * 是否存在set方法
   *
   * @param name 属性
   */
  public boolean hasSetter(String name) {
    // 分词器
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 子属性
    if (prop.hasNext()) {
      // 存在set, 递归调用
      if (reflector.hasSetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      // 无子属性
      return reflector.hasSetter(prop.getName());
    }
  }

  /**
   * 判断是否有get方法
   *
   * @param name 属性名
   */
  public boolean hasGetter(String name) {
    // 分词器
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 子属性
    if (prop.hasNext()) {
      // 存在get方法, 递归调用
      if (reflector.hasGetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop);
        return metaProp.hasGetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      // 无子属性, 直接判断
      return reflector.hasGetter(prop.getName());
    }
  }

  /**
   * 获取属性get方法的Invoker对象
   */
  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  /**
   * 获取属性set方法的Invoker对象
   */
  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  private StringBuilder buildProperty(String name, StringBuilder builder) {
    // 属性分词
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 存在子属性
    if (prop.hasNext()) {
      // 获取属性名字; 不区分大小写, 驼峰命名
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        builder.append(propertyName);
        builder.append(".");
        // 递归拼接子属性
        MetaClass metaProp = metaClassForProperty(propertyName);
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    } else {
      // 没有子属性, 添加到builder中
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

  /**
   * 判断是否存在默认构造器
   */
  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
