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
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.mybatis.spring.mapper.MapperFactoryBean;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

/**
 * Use this annotation to register MyBatis mapper interfaces when using Java Config. It performs when same work as
 * {@link MapperScannerConfigurer} via {@link MapperScannerRegistrar}.
 *
 * <p>
 * Either {@link #basePackageClasses} or {@link #basePackages} (or its alias {@link #value}) may be specified to define
 * specific packages to scan. Since 2.0.4, If specific packages are not defined, scanning will occur from the package of
 * the class that declares this annotation.
 *
 * <p>
 * Configuration example:
 * </p>
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;MapperScan("org.mybatis.spring.sample.mapper")
 * public class AppConfig {
 *
 *   &#064;Bean
 *   public DataSource dataSource() {
 *     return new EmbeddedDatabaseBuilder().addScript("schema.sql").build();
 *   }
 *
 *   &#064;Bean
 *   public DataSourceTransactionManager transactionManager() {
 *     return new DataSourceTransactionManager(dataSource());
 *   }
 *
 *   &#064;Bean
 *   public SqlSessionFactory sqlSessionFactory() throws Exception {
 *     SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
 *     sessionFactory.setDataSource(dataSource());
 *     return sessionFactory.getObject();
 *   }
 * }
 * </pre>
 *
 * @author Michael Lanyon
 * @author Eduardo Macarron
 * @author Qimiao Chen
 * @since 1.2.0
 * @see MapperScannerRegistrar
 * @see MapperFactoryBean
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(MapperScannerRegistrar.class) // ❗️❗️❗️ -> 使用@MapperScan的关键点 -> 向Spring容器引入MapperScannerRegistrar bean
@Repeatable(MapperScans.class)
public @interface MapperScan {
  // 使用 Java Config 时使用该注解注册 MyBatis 映射器接口。它通过MapperScannerRegistrar在与MapperScannerConfigurer相同的工作时执行。
  // 可以basePackageClasses或basePackages （或其别名value ）来定义要扫描的特定包。从 2.0.4 开始，如果没有定义特定的包，将从声明该注解的类的包开始进行扫描。
  // 配置示例：
  //   @Configuration
  //   @MapperScan("org.mybatis.spring.sample.mapper")
  //   public class AppConfig {
  //
  //     @Bean
  //     public DataSource dataSource() {
  //       return new EmbeddedDatabaseBuilder().addScript("schema.sql").build(); // 数据源
  //     }
  //
  //     @Bean
  //     public DataSourceTransactionManager transactionManager() {
  //       return new DataSourceTransactionManager(dataSource()); // 这是Spring的TransactionalManager,主要负责@Transactional注解
  //     }
  //
  //     @Bean
  //     public SqlSessionFactory sqlSessionFactory() throws Exception {
  //       SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
  //       sessionFactory.setDataSource(dataSource());
  //       return sessionFactory.getObject();
  //     }
  //   }


  // basePackage/sqlSessionFactoryRef/sqlSessionTemplateRef/lazyInitialization/defaultScope 可以使用Spring的占位符哦

  /**
   * Alias for the {@link #basePackages()} attribute. Allows for more concise annotation declarations e.g.:
   * {@code @MapperScan("org.my.pkg")} instead of {@code @MapperScan(basePackages = "org.my.pkg"})}.
   *
   * @return base package names
   */
  @AliasFor("basePackages")
  String[] value() default {};
  // 要扫描的Mapper文件的基础package

  /**
   * Base packages to scan for MyBatis interfaces. Note that only interfaces with at least one method will be
   * registered; concrete classes will be ignored.
   *
   * @return base package names for scanning mapper interface
   */
  @AliasFor("value")
  String[] basePackages() default {};
  // 等价 value 属性

  /**
   * Type-safe alternative to {@link #basePackages()} for specifying the packages to scan for annotated components. The
   * package of each class specified will be scanned.
   * <p>
   * Consider creating a special no-op marker class or interface in each package that serves no purpose other than being
   * referenced by this attribute.
   *
   * @return classes that indicate base package for scanning mapper interface
   */
  Class<?>[] basePackageClasses() default {};
  // 从指定Class所在的package作为basePackages进行扫描

  /**
   * The {@link BeanNameGenerator} class to be used for naming detected components within the Spring container.
   *
   * @return the class of {@link BeanNameGenerator}
   */
  Class<? extends BeanNameGenerator> nameGenerator() default BeanNameGenerator.class;
  // 命名 Spring 容器中检测到的组件的BeanNameGenerator类 -> 决定Mapper的beanName

  /**
   * This property specifies the annotation that the scanner will search for.
   * <p>
   * The scanner will register all interfaces in the base package that also have the specified annotation.
   * <p>
   * Note this can be combined with markerInterface.
   *
   * @return the annotation that the scanner will search for
   */
  Class<? extends Annotation> annotationClass() default Annotation.class;
  // 此属性指定扫描仪将搜索的注释 -- 默认是搜索@Mapper注解哦
  // ❗️❗️❗️

  /**
   * This property specifies the parent that the scanner will search for.
   * <p>
   * The scanner will register all interfaces in the base package that also have the specified interface class as a
   * parent.
   * <p>
   * Note this can be combined with annotationClass.
   *
   * @return the parent that the scanner will search for
   */
  Class<?> markerInterface() default Class.class;
  // 此属性指定扫描仪将搜索的父级。
  // 将注册basePackages中的所有接口，这些接口也具有指定的接口类作为父级。

  /**
   * Specifies which {@code SqlSessionTemplate} to use in the case that there is more than one in the spring context.
   * Usually this is only needed when you have more than one datasource.
   *
   * @return the bean name of {@code SqlSessionTemplate}
   */
  String sqlSessionTemplateRef() default "";
  // 指定在 spring 上下文中存在多个的情况下使用哪个SqlSessionTemplate 。通常只有当您有多个数据源时才需要这样做。
  // return：SqlSessionTemplate的 bean 名称

  /**
   * Specifies which {@code SqlSessionFactory} to use in the case that there is more than one in the spring context.
   * Usually this is only needed when you have more than one datasource.
   *
   * @return the bean name of {@code SqlSessionFactory}
   */
  String sqlSessionFactoryRef() default "";
  // 指定在 spring 上下文中存在多个 SqlSessionFactory 的情况下使用哪个SqlSessionFactory 。通常只有当您有多个数据源时才需要这样做。
  // 回报：SqlSessionFactory的 bean 名称

  /**
   * Specifies a custom MapperFactoryBean to return a mybatis proxy as spring bean.
   *
   * @return the class of {@code MapperFactoryBean}
   */
  Class<? extends MapperFactoryBean> factoryBean() default MapperFactoryBean.class;
  // 自定义的 MapperFactoryBean 以返回一个 mybatis 代理作为 spring bean。

  /**
   * Whether enable lazy initialization of mapper bean.
   *
   * <p>
   * Default is {@code false}.
   * </p>
   *
   * @return set {@code true} to enable lazy initialization
   * @since 2.0.2
   */
  String lazyInitialization() default "";
  // 是否启用 mapper bean 的延迟初始化

  /**
   * Specifies the default scope of scanned mappers.
   *
   * <p>
   * Default is {@code ""} (equiv to singleton).
   * </p>
   *
   * @return the default scope
   */
  String defaultScope() default AbstractBeanDefinition.SCOPE_DEFAULT;
  // 定义扫描的mapper bean的范围scope

}
