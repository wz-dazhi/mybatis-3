package org.apache.ibatis.submitted.custom_collection_handling;

import org.apache.ibatis.domain.misc.CustomBeanWrapperFactory;
import org.apache.ibatis.domain.misc.RichType;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.Test;

import java.util.HashMap;

/**
 * @projectName: mybatis-3
 * @package: org.apache.ibatis.submitted.custom_collection_handling
 * @className: Test
 * @description:
 * @author: zhi
 * @date: 2021/6/10
 * @version: 1.0
 */
public class TestObjectWrapper {

  @Test
  public void test01() {
    RichType object = new RichType();
    if (true) {
      object.setRichType(new RichType());
      object.getRichType().setRichMap(new HashMap());
      object.getRichType().getRichMap().put("nihao", 123);
    }

    MetaObject meta = MetaObject.forObject(object, SystemMetaObject.DEFAULT_OBJECT_FACTORY, new CustomBeanWrapperFactory(), new DefaultReflectorFactory());
    Class<?> clazz = meta.getObjectWrapper().getGetterType("richType.richMap.nihao");
    System.out.println(clazz);
  }
}
