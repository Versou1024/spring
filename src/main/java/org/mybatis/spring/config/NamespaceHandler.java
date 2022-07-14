/*
 * Copyright 2010-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mybatis.spring.config;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

/**
 * Namespace handler for the MyBatis namespace.
 *
 * @author Lishu Luo
 *
 * @see MapperScannerBeanDefinitionParser
 * @since 1.2.0
 */
public class NamespaceHandler extends NamespaceHandlerSupport {


  // 作用:
  // Spring通过XML解析程序将其解析为DOM树，通过NamespaceHandler指定对应的Namespace的BeanDefinitionParser将其转换成BeanDefinition。
  // 再通过Spring自身的功能对BeanDefinition实例化对象
  // 在期间，Spring还会加载两项资料：
  //  1.META-INF/spring.handlers
  //    指定NamespaceHandler(实现org.springframework.beans.factory.xml.NamespaceHandler)接口，
  //    或使用org.springframework.beans.factory.xml.NamespaceHandlerSupport的子类。
  //  2.META-INF/spring.schemas
  //    在解析XML文件时将XSD重定向到本地文件，避免在解析XML文件时需要上网下载XSD文件。通过现实org.xml.sax.EntityResolver接口来实现该功能

  // 测试场景:
  // 1. spring.handlers的内容
  // http\://test.hatter.me/schema/test=me.hatter.test.TestNamespaceHandler
  // 2. spring.schemas的内容
  // http\://test.hatter.me/schema/test/test.xsd=META-INF/test.xsd
  // 3. test.xsd的内容
  // <?xml version="1.0" encoding="UTF-8" standalone="no"?>
  // <xsd:schema xmlns="http://test.hatter.me/schema/test"
  //        xmlns:xsd="http://www.w3.org/2001/XMLSchema"
  //        targetNamespace="http://test.hatter.me/schema/test">
  //
  //        <xsd:element name="custom" type="customType">
  //        </xsd:element>
  //
  //        <xsd:complexType name="customType">
  //                <xsd:attribute name="id" type="xsd:ID">
  //                </xsd:attribute>
  //                <xsd:attribute name="name" type="xsd:string">
  //                </xsd:attribute>
  //        </xsd:complexType>
  //
  // </xsd:schema>
  // 4. me.hatter.test.TestNamespaceHandler
  // public class TestNamespaceHandler extends NamespaceHandlerSupport {
  //
  //    public void init() {
  //        registerBeanDefinitionParser("custom", new TestCustomBeanDefinitionParser());
  //    }
  // }
  // 5. me.hatter.test.TestCustomBeanDefinitionParser
  // public class TestCustomBeanDefinitionParser implements BeanDefinitionParser {
  //
  //    public BeanDefinition parse(Element element, ParserContext parserContext) {
  //
  //        String id = element.getAttribute("id"); // 处理id属性
  //        String name = element.getAttribute("name"); // 处理name属性
  //
  //        RootBeanDefinition beanDefinition = new RootBeanDefinition();
  //        beanDefinition.setBeanClass(TestBean.class);
  //        beanDefinition.getPropertyValues().addPropertyValue("name", name);
  //        parserContext.getRegistry().registerBeanDefinition(id, beanDefinition);
  //
  //        return beanDefinition;
  //    }
  //}
  // 6. test.xml
  // <?xml version="1.0" encoding="UTF-8"?>
  // <beans xmlns="http://www.springframework.org/schema/beans"
  //    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  //    xmlns:test="http://test.hatter.me/schema/test"
  //    xsi:schemaLocation="
  //        http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-2.5.xsd
  //        http://test.hatter.me/schema/test http://test.hatter.me/schema/test/test.xsd">
  //
  //        <test:custom id="testCustom" name="this is a test custom tag" />
  // </beans>
  // 7. 测试
  //  public static void main(String[] args) {
  //        String xml = "classpath:me/hatter/test/main/test.xml";
  //        ApplicationContext context = new ClassPathXmlApplicationContext(new String[] { xml });
  //        System.out.println(context.getBean("testCustom"));
  //  }


  // 更多详情见: https://blog.csdn.net/wabiaozia/article/details/78631259

  /**
   * {@inheritDoc}
   */
  @Override
  public void init() {
    // 主要是将: scan的标签下面的所有属性和子元素交给MapperScannerBeanDefinitionParser解析
    // 忽略~~ 现在几乎不使用xml
    registerBeanDefinitionParser("scan", new MapperScannerBeanDefinitionParser());
  }

}
