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

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 参数名解析
 */
public class ParamNameResolver {

  /**
   * 通用的参数前缀
   */
  public static final String GENERIC_NAME_PREFIX = "param";

  /**
   * 是否获取真实参数名, 默认为true
   */
  private final boolean useActualParamName;

  /**
   * RowBounds ResultHandler 此索引可能跟实际索引不同
   * <p>
   * The key is the index and the value is the name of the parameter.<br />
   * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
   * the parameter index is used. Note that this index could be different from the actual index
   * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
   * </p>
   * <ul>
   * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
   * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
   * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
   * </ul>
   * K: 参数顺序
   * V: 参数名
   */
  private final SortedMap<Integer, String> names;

  /**
   * 参数存在注解
   */
  private boolean hasParamAnnotation;

  public ParamNameResolver(Configuration config, Method method) {
    // 如果为true 不带注解@Param的情况下, mapper.xml中只能使用 #{arg0} #{arg1} ... 或者  #{param1} #{param2} ...
    // 如果为false 不带注解@Param的情况下, mapper.xml中只能使用  #{0} #{1} ... 或者  #{param1} #{param2} ...
    this.useActualParamName = config.isUseActualParamName();
//    this.useActualParamName = false;
    // 参数类型
    final Class<?>[] paramTypes = method.getParameterTypes();
    // 参数注解
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();
    // 默认参数map, 下标从0开始
    final SortedMap<Integer, String> map = new TreeMap<>();
    // 注解数量
    int paramCount = paramAnnotations.length;
    // get names from @Param annotations
    // 获取带有@Param的注解参数
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      // 跳过RowBounds ResultHandler
      if (isSpecialParameter(paramTypes[paramIndex])) {
        // skip special parameters
        continue;
      }
      String name = null;
      // 遍历当前参数的注解, 获取Param注解的value值; 一个参数可能存在多个注解
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        // 只要Param注解
        if (annotation instanceof Param) {
          hasParamAnnotation = true;
          name = ((Param) annotation).value();
          break;
        }
      }
      // 参数没带Param注解
      if (name == null) {
        // @Param was not specified.
        if (useActualParamName) {
          // 根据参数索引获取当前方法参数名称 arg0 arg1 arg2 ...
          name = getActualParamName(method, paramIndex);
        }
        // 如果参数名称还是为空, 则默认从0开始 0 1 2 ...
        if (name == null) {
          // use the parameter index as the name ("0", "1", ...)
          // gcode issue #71
          name = String.valueOf(map.size());
        }
      }
      // 参数索引, 参数名称
      map.put(paramIndex, name);
    }
    // 转换成不可变的map
    names = Collections.unmodifiableSortedMap(map);
  }

  /**
   * 根据索引获取方法上的参数名称 arg0 arg1 ...
   */
  private String getActualParamName(Method method, int paramIndex) {
    return ParamNameUtil.getParamNames(method).get(paramIndex);
  }

  /**
   * 判断参数是否属于RowBounds ResultHandler的子类
   */
  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }

  /**
   * Returns parameter names referenced by SQL providers.
   * 返回Mapper接口方法参数的名称列表, 供SQL解析
   *
   * @return the names
   */
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

  /**
   * <p>
   * A single non-special parameter is returned without a name.
   * Multiple parameters are named using the naming rule.
   * In addition to the default names, this method also adds the generic names (param1, param2,
   * ...).
   * </p>
   *
   * @param args the args
   * @return the named params
   */
  public Object getNamedParams(Object[] args) {
    // 参数数量
    final int paramCount = names.size();
    if (args == null || paramCount == 0) {
      return null;
    } else if (!hasParamAnnotation && paramCount == 1) {
      // 没有带Param注解, 并且只有一个参数
      Object value = args[names.firstKey()];
      // 如果参数是集合, 转换成map
      return wrapToMapIfCollection(value, useActualParamName ? names.get(0) : null);
    } else {
      // 遍历参数, 添加通用参数到ParamMap中. 通用参数 (param1, param2, ...)
      final Map<String, Object> param = new ParamMap<>();
      int i = 0;
      for (Map.Entry<Integer, String> entry : names.entrySet()) {
        param.put(entry.getValue(), args[entry.getKey()]);
        // add generic param names (param1, param2, ...)
        final String genericParamName = GENERIC_NAME_PREFIX + (i + 1);
        // ensure not to overwrite parameter named with @Param
        if (!names.containsValue(genericParamName)) {
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }

  /**
   * Wrap to a {@link ParamMap} if object is {@link Collection} or array.
   * 如果是Collection 或者 数组, 包装到ParamMap中
   *
   * @param object          a parameter object
   * @param actualParamName an actual parameter name
   *                        (If specify a name, set an object to {@link ParamMap} with specified name)
   * @return a {@link ParamMap}
   * @since 3.5.5
   */
  public static Object wrapToMapIfCollection(Object object, String actualParamName) {
    // 集合
    if (object instanceof Collection) {
      ParamMap<Object> map = new ParamMap<>();
      map.put("collection", object);
      if (object instanceof List) {
        map.put("list", object);
      }
      Optional.ofNullable(actualParamName)
        .ifPresent(name -> map.put(name, object));
      return map;
    } else if (object != null && object.getClass().isArray()) {
      // 数组
      ParamMap<Object> map = new ParamMap<>();
      map.put("array", object);
      Optional.ofNullable(actualParamName)
        .ifPresent(name -> map.put(name, object));
      return map;
    }
    return object;
  }

}
