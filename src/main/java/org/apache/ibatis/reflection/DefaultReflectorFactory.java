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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 反射器默认工厂
 */
public class DefaultReflectorFactory implements ReflectorFactory {
  /**
   * 默认开启缓存
   */
  private boolean classCacheEnabled = true;
  /**
   * 并发Map缓存
   */
  private final ConcurrentMap<Class<?>, Reflector> reflectorMap = new ConcurrentHashMap<>();

  public DefaultReflectorFactory() {
  }

  @Override
  public boolean isClassCacheEnabled() {
    return classCacheEnabled;
  }

  @Override
  public void setClassCacheEnabled(boolean classCacheEnabled) {
    this.classCacheEnabled = classCacheEnabled;
  }

  @Override
  public Reflector findForClass(Class<?> type) {
    // 开启缓存, 从容器中获取
    if (classCacheEnabled) {
      // synchronized (type) removed see issue #461
      // 容器中不存在, 则创建新的
      return reflectorMap.computeIfAbsent(type, Reflector::new);
    } else {
      // 不用缓存, 直接创建新的反射器
      return new Reflector(type);
    }
  }

}
