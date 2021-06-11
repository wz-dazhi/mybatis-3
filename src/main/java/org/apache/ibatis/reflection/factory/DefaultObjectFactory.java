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
package org.apache.ibatis.reflection.factory;

import org.apache.ibatis.reflection.ReflectionException;
import org.apache.ibatis.reflection.Reflector;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * @author Clinton Begin
 */
public class DefaultObjectFactory implements ObjectFactory, Serializable {

  private static final long serialVersionUID = -8855120656740914948L;

  @Override
  public <T> T create(Class<T> type) {
    return create(type, null, null);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    // 解析类对应的接口返回对应的实现类
    Class<?> classToCreate = resolveInterface(type);
    // we know types are assignable
    // 实例化对象
    return (T) instantiateClass(classToCreate, constructorArgTypes, constructorArgs);
  }

  private <T> T instantiateClass(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    try {
      Constructor<T> constructor;
      // 构造参数为空, 根据默认构造器实例化对象
      if (constructorArgTypes == null || constructorArgs == null) {
        constructor = type.getDeclaredConstructor();
        try {
          return constructor.newInstance();
        } catch (IllegalAccessException e) {
          // 私有构造器设置可访问
          if (Reflector.canControlMemberAccessible()) {
            constructor.setAccessible(true);
            return constructor.newInstance();
          } else {
            throw e;
          }
        }
      }
      // 根据指定参数实例化构造器
      constructor = type.getDeclaredConstructor(constructorArgTypes.toArray(new Class[0]));
      try {
        return constructor.newInstance(constructorArgs.toArray(new Object[0]));
      } catch (IllegalAccessException e) {
        // 私有构造器设置可访问
        if (Reflector.canControlMemberAccessible()) {
          constructor.setAccessible(true);
          return constructor.newInstance(constructorArgs.toArray(new Object[0]));
        } else {
          throw e;
        }
      }
    } catch (Exception e) {
      // 异常打印. 获取参数的类型和参数的值
      String argTypes = Optional.ofNullable(constructorArgTypes)
        .orElseGet(Collections::emptyList)
        .stream()
        .map(Class::getSimpleName)
        .collect(Collectors.joining(","));
      String argValues = Optional.ofNullable(constructorArgs)
        .orElseGet(Collections::emptyList)
        .stream()
        .map(String::valueOf)
        .collect(Collectors.joining(","));
      throw new ReflectionException("Error instantiating " + type + " with invalid types (" + argTypes + ") or values (" + argValues + "). Cause: " + e, e);
    }
  }

  /**
   * 传入接口, 返回对应的实现类.
   * 传入普通类, 返回对象的Class
   *
   * @param type
   * @return
   */
  protected Class<?> resolveInterface(Class<?> type) {
    Class<?> classToCreate;
    if (type == List.class || type == Collection.class || type == Iterable.class) {
      classToCreate = ArrayList.class;
    } else if (type == Map.class) {
      classToCreate = HashMap.class;
    } else if (type == SortedSet.class) { // issue #510 Collections Support
      classToCreate = TreeSet.class;
    } else if (type == Set.class) {
      classToCreate = HashSet.class;
    } else {
      classToCreate = type;
    }
    return classToCreate;
  }

  @Override
  public <T> boolean isCollection(Class<T> type) {
    return Collection.class.isAssignableFrom(type);
  }

  public static void main(String[] args) {
    DefaultObjectFactory factory = new DefaultObjectFactory();
//    Class<?> aClass = factory.resolveInterface(Object.class);
//    Class<?> aClass = factory.resolveInterface(HashSet.class);
    Class<?> aClass = factory.resolveInterface(Collection.class);
    System.out.println(aClass);
  }

}
