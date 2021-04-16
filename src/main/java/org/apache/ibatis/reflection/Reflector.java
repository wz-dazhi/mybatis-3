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

import org.apache.ibatis.reflection.invoker.*;
import org.apache.ibatis.reflection.property.PropertyNamer;

import java.lang.reflect.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.Map.Entry;

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 * 反射器, 一个类对应一个反射器
 *
 * @author Clinton Begin
 */
public class Reflector {

  /**
   * 反射类的Class 对象
   */
  private final Class<?> type;
  /**
   * 可读属性名称
   */
  private final String[] readablePropertyNames;
  /**
   * 可写属性名称
   */
  private final String[] writablePropertyNames;
  /**
   * set 方法
   */
  private final Map<String, Invoker> setMethods = new HashMap<>();
  /**
   * get 方法
   */
  private final Map<String, Invoker> getMethods = new HashMap<>();
  /**
   * set 方法 Class对象
   */
  private final Map<String, Class<?>> setTypes = new HashMap<>();
  /**
   * get 方法 Class对象
   */
  private final Map<String, Class<?>> getTypes = new HashMap<>();
  /**
   * 默认构造器
   */
  private Constructor<?> defaultConstructor;

  /**
   * 不区分大小写的属性集合 key:toUpperCase的属性名称 value：原属性名称
   */
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  /**
   * 放射器构造方法
   *
   * @param clazz 要反射的class对象
   */
  public Reflector(Class<?> clazz) {
    type = clazz;
    // 添加默认构造方法
    addDefaultConstructor(clazz);
    // 添加get方法
    addGetMethods(clazz);
    // 添加set方法
    addSetMethods(clazz);
    // 添加所有的字段
    addFields(clazz);
    // get方法的变量转换成可读变量数组
    readablePropertyNames = getMethods.keySet().toArray(new String[0]);
    // set方法的变量转换成可写变量数组
    writablePropertyNames = setMethods.keySet().toArray(new String[0]);
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  private void addDefaultConstructor(Class<?> clazz) {
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    // stream 流遍历所有构造方法, 过滤无参的构造方法, 如果不为空 -> 并设置为默认构造器
    Arrays.stream(constructors)
      .filter(constructor -> constructor.getParameterTypes().length == 0)
      .findAny()
      .ifPresent(constructor -> this.defaultConstructor = constructor);
  }

  private void addGetMethods(Class<?> clazz) {
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    // 获得class所有的方法
    Method[] methods = getClassMethods(clazz);
    // 遍历所有方法, 过滤不带参数的, 并且开头是get 或者 is的方法
    Arrays.stream(methods)
      .filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));
    // 解决冲突的get方法
    resolveGetterConflicts(conflictingGetters);
  }

  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      // 最终选定的方法
      Method winner = null;
      // 变量名称
      String propName = entry.getKey();
      // 该变量表示是否模糊不清的(存在方法名多个相同的get方法)
      boolean isAmbiguous = false;
      // 遍历所有的get方法
      for (Method candidate : entry.getValue()) {
        // 等于空, 赋值. 继续循环
        if (winner == null) {
          winner = candidate;
          continue;
        }
        // 不等于空, 说明已经赋值了. 判断冲突get方法的返回类型
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();
        // 1. 两个返回类型相等
        if (candidateType.equals(winnerType)) {
          // 返回类型不等于 boolean, 结束内循环 break
          if (!boolean.class.equals(candidateType)) {
            isAmbiguous = true;
            break;
          } else if (candidate.getName().startsWith("is")) {
            // 等于boolean, 并且是 is开头, 重新赋值给winner
            winner = candidate;
          }
          // 2. 候补返回类型是否是选定方法返回类型的父类, 如果是则使用子类.
          // 比如 winnerType = ArrayList, candidateType = List.  则使用子类ArrayList
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
        } else if (winnerType.isAssignableFrom(candidateType)) {
          // 3. 如果winnerType 是 candidate的父类. 则使用子类candidateType
          winner = candidate;
        } else {
          isAmbiguous = true;
          break;
        }
      }
      // 添加get方法到 getMethods getTypes
      addGetMethod(propName, winner, isAmbiguous);
    }
  }

  private void addGetMethod(String name, Method method, boolean isAmbiguous) {
    MethodInvoker invoker = isAmbiguous
      ? new AmbiguousMethodInvoker(method, MessageFormat.format(
      "Illegal overloaded getter method with ambiguous type for property ''{0}'' in class ''{1}''. This breaks the JavaBeans specification and can cause unpredictable results.",
      name, method.getDeclaringClass().getName()))
      : new MethodInvoker(method);
    getMethods.put(name, invoker);
    Type returnType = TypeParameterResolver.resolveReturnType(method, type);
    getTypes.put(name, typeToClass(returnType));
  }

  private void addSetMethods(Class<?> clazz) {
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    // 获取所有的方法, 过滤只有一个参数 并且是set开头的方法
    Method[] methods = getClassMethods(clazz);
    Arrays.stream(methods)
      .filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
    // 解决set冲突的方法
    resolveSetterConflicts(conflictingSetters);
  }

  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    // 校验变量名称, 是否属于普通的变量
    if (isValidPropertyName(name)) {
      // 变量名不存在, 返回 new ArrayList.
      // 变量名存在, 使用之前的List
      List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
      // 变量名对应的方法放入list中
      list.add(method);
    }
  }

  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (Entry<String, List<Method>> entry : conflictingSetters.entrySet()) {
      // 变量名
      String propName = entry.getKey();
      // 变量名的所有set方法
      List<Method> setters = entry.getValue();
      // 获取变量get方法的返回类型
      Class<?> getterType = getTypes.get(propName);
      // get方法是否存在多个
      boolean isGetterAmbiguous = getMethods.get(propName) instanceof AmbiguousMethodInvoker;
      boolean isSetterAmbiguous = false;
      // 最佳匹配
      Method match = null;
      for (Method setter : setters) {
        // 只有一个get方法, 并且get方法返回值和set方法参数类型一致
        if (!isGetterAmbiguous && setter.getParameterTypes()[0].equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        // 存在多个set方法, 匹配最佳set方法
        if (!isSetterAmbiguous) {
          match = pickBetterSetter(match, setter, propName);
          isSetterAmbiguous = match == null;
        }
      }
      // 添加最佳匹配的set方法
      if (match != null) {
        addSetMethod(propName, match);
      }
    }
  }

  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    // match == null return setter
    if (setter1 == null) {
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    // 返回子类set
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    // 存在多个setter方法
    MethodInvoker invoker = new AmbiguousMethodInvoker(setter1,
      MessageFormat.format(
        "Ambiguous setters defined for property ''{0}'' in class ''{1}'' with types ''{2}'' and ''{3}''.",
        property, setter2.getDeclaringClass().getName(), paramType1.getName(), paramType2.getName()));
    setMethods.put(property, invoker);
    // 解析set方法的参数
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(setter1, type);
    // 获取set方法参数的具体Class
    setTypes.put(property, typeToClass(paramTypes[0]));
    return null;
  }

  private void addSetMethod(String name, Method method) {
    // 将最佳匹配的set方法放入setMethods, setTypes
    MethodInvoker invoker = new MethodInvoker(method);
    setMethods.put(name, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
    setTypes.put(name, typeToClass(paramTypes[0]));
  }

  /**
   * 寻找type对应的Class
   *
   * @param src
   * @return
   */
  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    // 普通类
    if (src instanceof Class) {
      result = (Class<?>) src;
      // 泛型类型, 使用泛型
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
      // 泛型数组, 获取具体类
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      // 普通类
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        // 递归调用, 寻找具体类
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    // 找不到, 使用Object
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  private void addFields(Class<?> clazz) {
    // 获取所有字段
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      // 字段没有set方法的情况
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        // 获取字段的访问修饰符
        int modifiers = field.getModifiers();
        // 排除静态常量, 因为final static最终会由classLoader进行处理
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          addSetField(field);
        }
      }
      // 字段没有get方法情况
      if (!getMethods.containsKey(field.getName())) {
        addGetField(field);
      }
    }
    // 递归处理父类
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  private void addSetField(Field field) {
    // 将不存在set方法的字段, 放入setMethods setTypes
    if (isValidPropertyName(field.getName())) {
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    // 将不存在get方法的字段, 放入getMethods getTypes
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /**
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler <code>Class.getMethods()</code>,
   * because we want to look for private methods as well.
   *
   * @param clazz The class
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> clazz) {
    Map<String, Method> uniqueMethods = new HashMap<>();
    Class<?> currentClass = clazz;
    // 循环向上(父类)遍历, Object类除外
    while (currentClass != null && currentClass != Object.class) {
      // 添加类方法
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      // 获取class的接口, 因为class可能是一个抽象类
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }

      // 返回父类class
      currentClass = currentClass.getSuperclass();
    }

    // 返回所有的方法
    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[0]);
  }

  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      // 当前方法不属于桥接方法, 关于桥接方法: https://www.zhihu.com/question/54895701/answer/141623158
      if (!currentMethod.isBridge()) {
        // 获得方法签名
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        // 避免重复添加
        if (!uniqueMethods.containsKey(signature)) {
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  /**
   * 获得方法签名
   *
   * @param method
   * @return <p>
   * 返回格式: returnType#方法名:参数1,参数2,参数3
   * 实例如下:
   * 无返回值: void#setAge:int
   * 返回基础类型: int#getParams:java.lang.Integer,java.lang.Long
   * 返回引用类型: class java.lang.Boolean#int,long
   * </p>
   */
  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * Checks whether can control member accessible.
   *
   * @return If can control member accessible, it return {@literal true}
   * @since 3.5.0
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets the type for a property getter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.containsKey(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.containsKey(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
