/*
 * Copyright 2010-2022 the original author or authors.
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
package org.mybatis.spring.annotation;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * A {@link ImportBeanDefinitionRegistrar} to allow annotation configuration of MyBatis mapper scanning. Using
 * an @Enable annotation allows beans to be registered via @Component configuration, whereas implementing
 * {@code BeanDefinitionRegistryPostProcessor} will work for XML configuration.
 *
 * @author Michael Lanyon
 * @author Eduardo Macarron
 * @author Putthiphong Boonphong
 *
 * @see MapperFactoryBean
 * @see ClassPathMapperScanner
 * @since 1.2.0
 */
public class MapperScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {
  // 位于: org.mybatis.spring.annotation -> 表明@MapperScan仅仅关联哦

  // 核心: ❗️❗️❗️

  // 命名:
  // Mapper Scanner Registrar -> 主要是将 Mapper Scanner 扫描器相关类的加载到ioc容器中
  // 例如: MapperScannerConfigurer配置中心

  // 使用:
  // 当使用@MapperScan时需要注意一点: -- 那就是其他有一个元注解 @Import(MapperScannerRegistrar.class)
  // 对应这里的 MapperScannerRegistrar 实现的是 ImportBeanDefinitionRegistrar
  // 在Spring中当 ImportBeanDefinitionRegistrar#registerBeanDefinitions() 执行的时候
  // -- 项目中的配置类\项目中的组件类\项目中配置类中的@Bean方法\项目中@Import的ImportSelector和DerffedImportSelector导入类的也被递归导入\
  // -- 甚至包括SpringBoot中常用的spring.factories中外部配置类\外部配置类的@Bean方法都会先加载到BeanDefinitionRegistry

  // 核心作用:
  // 1. 向BeanDefinitionRegistry中注册MapperScannerConfigurer配置中心
  // 2. 并将@MapperScan的相关属性设置到MapperScannerConfigurer配置中心上去

  /**
   * {@inheritDoc}
   *
   * @deprecated Since 2.0.2, this method not used never.
   */
  @Override
  @Deprecated
  public void setResourceLoader(ResourceLoader resourceLoader) {
    // NOP
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

    // 1. importingClassMetadata 就是元注解有@Import(MapperScannerRegistrar.class)[实际就是类上有@MapperScan即可]的类的注解元数据
    AnnotationAttributes mapperScanAttrs = AnnotationAttributes.fromMap(importingClassMetadata.getAnnotationAttributes(MapperScan.class.getName()));
    // 2. 拿到@MapperScan开始准备
    if (mapperScanAttrs != null) {
      registerBeanDefinitions(importingClassMetadata, mapperScanAttrs, registry, generateBaseBeanName(importingClassMetadata, 0));
    }
  }

  void registerBeanDefinitions(AnnotationMetadata annoMeta, AnnotationAttributes annoAttrs, BeanDefinitionRegistry registry, String beanName) {

    // 1. 注入的beanClass默认为MapperScannerConfigurer
    // 向MapperScannerConfigurer设置系列的属性 -- 期间会搭配@MapperScan中的属性值
    BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(MapperScannerConfigurer.class);
    builder.addPropertyValue("processPropertyPlaceHolders", true);

    Class<? extends Annotation> annotationClass = annoAttrs.getClass("annotationClass");
    if (!Annotation.class.equals(annotationClass)) {
      builder.addPropertyValue("annotationClass", annotationClass);
    }

    Class<?> markerInterface = annoAttrs.getClass("markerInterface");
    if (!Class.class.equals(markerInterface)) {
      builder.addPropertyValue("markerInterface", markerInterface);
    }

    Class<? extends BeanNameGenerator> generatorClass = annoAttrs.getClass("nameGenerator");
    if (!BeanNameGenerator.class.equals(generatorClass)) {
      builder.addPropertyValue("nameGenerator", BeanUtils.instantiateClass(generatorClass));
    }

    Class<? extends MapperFactoryBean> mapperFactoryBeanClass = annoAttrs.getClass("factoryBean");
    if (!MapperFactoryBean.class.equals(mapperFactoryBeanClass)) {
      builder.addPropertyValue("mapperFactoryBeanClass", mapperFactoryBeanClass);
    }

    String sqlSessionTemplateRef = annoAttrs.getString("sqlSessionTemplateRef");
    if (StringUtils.hasText(sqlSessionTemplateRef)) {
      builder.addPropertyValue("sqlSessionTemplateBeanName", annoAttrs.getString("sqlSessionTemplateRef"));
    }

    String sqlSessionFactoryRef = annoAttrs.getString("sqlSessionFactoryRef");
    if (StringUtils.hasText(sqlSessionFactoryRef)) {
      builder.addPropertyValue("sqlSessionFactoryBeanName", annoAttrs.getString("sqlSessionFactoryRef"));
    }

    // 2. 要扫描的基本package

    List<String> basePackages = new ArrayList<>();

    basePackages.addAll(Arrays.stream(annoAttrs.getStringArray("basePackages")).filter(StringUtils::hasText)
        .collect(Collectors.toList()));

    basePackages.addAll(Arrays.stream(annoAttrs.getClassArray("basePackageClasses")).map(ClassUtils::getPackageName)
        .collect(Collectors.toList()));

    // 2.1 当没有指定basePackage和basePackageClasses时,将指定默认扫描的路径为: 使用@MapperScan的配置类的package
    if (basePackages.isEmpty()) {
      basePackages.add(getDefaultBasePackage(annoMeta));
    }

    String lazyInitialization = annoAttrs.getString("lazyInitialization");
    if (StringUtils.hasText(lazyInitialization)) {
      builder.addPropertyValue("lazyInitialization", lazyInitialization);
    }

    String defaultScope = annoAttrs.getString("defaultScope");
    if (!AbstractBeanDefinition.SCOPE_DEFAULT.equals(defaultScope)) {
      builder.addPropertyValue("defaultScope", defaultScope);
    }

    builder.addPropertyValue("basePackage", StringUtils.collectionToCommaDelimitedString(basePackages));

    // 2.2 设置bean的角色Role
    builder.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

    registry.registerBeanDefinition(beanName, builder.getBeanDefinition());

    // 接下来主要是看 MapperScannerConfigurer 在实例化过程的干了什么/以及实例化之后做了写什么

  }

  private static String generateBaseBeanName(AnnotationMetadata importingClassMetadata, int index) {
    // 生成注入的MapperScannerConfigurer的BeanName
    // 默认是:使用@MapperScan的类的名字 + # + MapperScannerRegistrar + # + index

    return importingClassMetadata.getClassName() + "#" + MapperScannerRegistrar.class.getSimpleName() + "#" + index;
  }

  private static String getDefaultBasePackage(AnnotationMetadata importingClassMetadata) {
    // 就是类的全限定类名去除类名即得到

    return ClassUtils.getPackageName(importingClassMetadata.getClassName());
  }

  /**
   * A {@link MapperScannerRegistrar} for {@link MapperScans}.
   *
   * @since 2.0.0
   */
  static class RepeatingRegistrar extends MapperScannerRegistrar {
    /**
     * {@inheritDoc}
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
      AnnotationAttributes mapperScansAttrs = AnnotationAttributes
          .fromMap(importingClassMetadata.getAnnotationAttributes(MapperScans.class.getName()));
      if (mapperScansAttrs != null) {
        AnnotationAttributes[] annotations = mapperScansAttrs.getAnnotationArray("value");
        for (int i = 0; i < annotations.length; i++) {
          registerBeanDefinitions(importingClassMetadata, annotations[i], registry,
              generateBaseBeanName(importingClassMetadata, i));
        }
      }
    }
  }

}
