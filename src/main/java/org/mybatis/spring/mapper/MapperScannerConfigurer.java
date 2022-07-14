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
package org.mybatis.spring.mapper;

import static org.springframework.util.Assert.notNull;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScannerRegistrar;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PropertyResourceConfigurer;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * BeanDefinitionRegistryPostProcessor that searches recursively starting from a base package for interfaces and
 * registers them as {@code MapperFactoryBean}. Note that only interfaces with at least one method will be registered;
 * concrete classes will be ignored.
 * <p>
 * This class was a {code BeanFactoryPostProcessor} until 1.0.1 version. It changed to
 * {@code BeanDefinitionRegistryPostProcessor} in 1.0.2. See https://jira.springsource.org/browse/SPR-8269 for the
 * details.
 * <p>
 * The {@code basePackage} property can contain more than one package name, separated by either commas or semicolons.
 * <p>
 * This class supports filtering the mappers created by either specifying a marker interface or an annotation. The
 * {@code annotationClass} property specifies an annotation to search for. The {@code markerInterface} property
 * specifies a parent interface to search for. If both properties are specified, mappers are added for interfaces that
 * match <em>either</em> criteria. By default, these two properties are null, so all interfaces in the given
 * {@code basePackage} are added as mappers.
 * <p>
 * This configurer enables autowire for all the beans that it creates so that they are automatically autowired with the
 * proper {@code SqlSessionFactory} or {@code SqlSessionTemplate}. If there is more than one {@code SqlSessionFactory}
 * in the application, however, autowiring cannot be used. In this case you must explicitly specify either an
 * {@code SqlSessionFactory} or an {@code SqlSessionTemplate} to use via the <em>bean name</em> properties. Bean names
 * are used rather than actual objects because Spring does not initialize property placeholders until after this class
 * is processed.
 * <p>
 * Passing in an actual object which may require placeholders (i.e. DB user password) will fail. Using bean names defers
 * actual object creation until later in the startup process, after all placeholder substitution is completed. However,
 * note that this configurer does support property placeholders of its <em>own</em> properties. The
 * <code>basePackage</code> and bean name properties all support <code>${property}</code> style substitution.
 * <p>
 * Configuration sample:
 *
 * <pre class="code">
 * {@code
 *   <bean class="org.mybatis.spring.mapper.MapperScannerConfigurer">
 *       <property name="basePackage" value="org.mybatis.spring.sample.mapper" />
 *       <!-- optional unless there are multiple session factories defined -->
 *       <property name="sqlSessionFactoryBeanName" value="sqlSessionFactory" />
 *   </bean>
 * }
 * </pre>
 *
 * @author Hunter Presnall
 * @author Eduardo Macarron
 *
 * @see MapperFactoryBean
 * @see ClassPathMapperScanner
 */
public class MapperScannerConfigurer implements BeanDefinitionRegistryPostProcessor, InitializingBean, ApplicationContextAware, BeanNameAware {
  // 命名:
  // MapperScannerConfigurer =  Mapper扫描器的配置中心
  // 配置中心的含义是@MapperScan的注解属性都将被配置到当前类的各个字段上

  // 继承体系:
  // 实现 BeanDefinitionRegistryPostProcessor/InitializingBean/ApplicationContextAware/BeanNameAware 接口

  // 注册过程:
  // 当前类会被注册到ioc容器中通过 @MapperScan -> @Import(MapperScannerRegistrar.class) -> MapperScannerRegistrar.registerBeanDefinitions()

  // 核心作用:
  // MapperScannerConfigurer 主要就将@MapperScan中的属性设置到这里,然后并在postProcessBeanDefinitionRegistry()方法中
  // 构造了❗️️ClassPathMapperScanner❗️去对所有的Mapper进行扫描哦


  // 此属性允许您为mapper接口的文件设置基本扫描包。您可以使用分号或逗号作为分隔符来设置多个包。
  // 将从指定的包开始递归地搜索映射器
  // 和@MapperScan的basePackages/value/basePackageClasses有关哦
  // 注意: 当没有指定@MapperScan的basePackage或basePackageClasses属性,将指定默认扫描的basePackages为: 带有@MapperScan的配置类的package
  private String basePackage;

  // 是否将映射器mapper接口添加到MyBatis,即添加到Mybatis的Configurations类中
  private boolean addToConfig = true;

  // 表示是否将扫描到的mapper bean都进行延迟初始化
  // 和@MapperScan的lazyInitialization有关
  private String lazyInitialization;

  // 已弃用
  private SqlSessionFactory sqlSessionFactory;

  // 已弃用
  private SqlSessionTemplate sqlSessionTemplate;

  // 指定在 spring 上下文中存在多个的情况下使用哪个SqlSessionTemplate 。通常只有当您有多个数据源时才需要这样做。
  // 和@MapperScan的sqlSessionFactoryRef值有关
  private String sqlSessionFactoryBeanName;

  // 指定在 spring 上下文中存在多个 SqlSessionFactory 的情况下使用哪个SqlSessionFactory 。通常只有当您有多个数据源时才需要这样做。
  // 和@MapperScan的sqlSessionTemplateRef值有关
  private String sqlSessionTemplateBeanName;

  // 待扫描的类上必须标注的注解
  // 等价 = @MapperScan的annotationClass
  // 默认是扫描@Mapper接口
  private Class<? extends Annotation> annotationClass;

  // 待扫描的类上必须实现的接口
  // = @MapperScan的markerInterface
  private Class<?> markerInterface;

  // 和@MapperScan的factoryBean属性有关
  // 默认情况下就是: MapperFactoryBean -- 用户可以通过继承MapperFactoryBean然后通过@MapperScan指定过来
  private Class<? extends MapperFactoryBean> mapperFactoryBeanClass;

  private ApplicationContext applicationContext;

  // 当前类被注入到ioc容器的
  private String beanName;

  // 是否需要执行属性占位符处理的标志 -> 主要是对@MapperScan的属性进行占位符处理
  // 在MapperScannerRegistrar中默认值为true -> 意味着需要执行属性占位符处理
  private boolean processPropertyPlaceHolders;

  // 与@MapperScan的nameGenerator指定的beanName有关
  private BeanNameGenerator nameGenerator;

  // 和@MapperScan的defaultScope有关 -- 比如是单例/原型等等
  private String defaultScope;

  /**
   * This property lets you set the base package for your mapper interface files.
   * <p>
   * You can set more than one package by using a semicolon or comma as a separator.
   * <p>
   * Mappers will be searched for recursively starting in the specified package(s).
   *
   * @param basePackage
   *          base package name
   */
  public void setBasePackage(String basePackage) {
    this.basePackage = basePackage;
  }

  /**
   * Same as {@code MapperFactoryBean#setAddToConfig(boolean)}.
   *
   * @param addToConfig
   *          a flag that whether add mapper to MyBatis or not
   * @see MapperFactoryBean#setAddToConfig(boolean)
   */
  public void setAddToConfig(boolean addToConfig) {
    this.addToConfig = addToConfig;
  }

  /**
   * Set whether enable lazy initialization for mapper bean.
   * <p>
   * Default is {@code false}.
   * </p>
   *
   * @param lazyInitialization
   *          Set the @{code true} to enable
   * @since 2.0.2
   */
  public void setLazyInitialization(String lazyInitialization) {
    this.lazyInitialization = lazyInitialization;
  }

  /**
   * This property specifies the annotation that the scanner will search for.
   * <p>
   * The scanner will register all interfaces in the base package that also have the specified annotation.
   * <p>
   * Note this can be combined with markerInterface.
   *
   * @param annotationClass
   *          annotation class
   */
  public void setAnnotationClass(Class<? extends Annotation> annotationClass) {
    this.annotationClass = annotationClass;
  }

  /**
   * This property specifies the parent that the scanner will search for.
   * <p>
   * The scanner will register all interfaces in the base package that also have the specified interface class as a
   * parent.
   * <p>
   * Note this can be combined with annotationClass.
   *
   * @param superClass
   *          parent class
   */
  public void setMarkerInterface(Class<?> superClass) {
    this.markerInterface = superClass;
  }

  /**
   * Specifies which {@code SqlSessionTemplate} to use in the case that there is more than one in the spring context.
   * Usually this is only needed when you have more than one datasource.
   * <p>
   *
   * @deprecated Use {@link #setSqlSessionTemplateBeanName(String)} instead
   *
   * @param sqlSessionTemplate
   *          a template of SqlSession
   */
  @Deprecated
  public void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
    this.sqlSessionTemplate = sqlSessionTemplate;
  }

  /**
   * Specifies which {@code SqlSessionTemplate} to use in the case that there is more than one in the spring context.
   * Usually this is only needed when you have more than one datasource.
   * <p>
   * Note bean names are used, not bean references. This is because the scanner loads early during the start process and
   * it is too early to build mybatis object instances.
   *
   * @since 1.1.0
   *
   * @param sqlSessionTemplateName
   *          Bean name of the {@code SqlSessionTemplate}
   */
  public void setSqlSessionTemplateBeanName(String sqlSessionTemplateName) {
    this.sqlSessionTemplateBeanName = sqlSessionTemplateName;
  }

  /**
   * Specifies which {@code SqlSessionFactory} to use in the case that there is more than one in the spring context.
   * Usually this is only needed when you have more than one datasource.
   * <p>
   *
   * @deprecated Use {@link #setSqlSessionFactoryBeanName(String)} instead.
   *
   * @param sqlSessionFactory
   *          a factory of SqlSession
   */
  @Deprecated
  public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }

  /**
   * Specifies which {@code SqlSessionFactory} to use in the case that there is more than one in the spring context.
   * Usually this is only needed when you have more than one datasource.
   * <p>
   * Note bean names are used, not bean references. This is because the scanner loads early during the start process and
   * it is too early to build mybatis object instances.
   *
   * @since 1.1.0
   *
   * @param sqlSessionFactoryName
   *          Bean name of the {@code SqlSessionFactory}
   */
  public void setSqlSessionFactoryBeanName(String sqlSessionFactoryName) {
    this.sqlSessionFactoryBeanName = sqlSessionFactoryName;
  }

  /**
   * Specifies a flag that whether execute a property placeholder processing or not.
   * <p>
   * The default is {@literal false}. This means that a property placeholder processing does not execute.
   *
   * @since 1.1.1
   *
   * @param processPropertyPlaceHolders
   *          a flag that whether execute a property placeholder processing or not
   */
  public void setProcessPropertyPlaceHolders(boolean processPropertyPlaceHolders) {
    this.processPropertyPlaceHolders = processPropertyPlaceHolders;
  }

  /**
   * The class of the {@link MapperFactoryBean} to return a mybatis proxy as spring bean.
   *
   * @param mapperFactoryBeanClass
   *          The class of the MapperFactoryBean
   * @since 2.0.1
   */
  public void setMapperFactoryBeanClass(Class<? extends MapperFactoryBean> mapperFactoryBeanClass) {
    this.mapperFactoryBeanClass = mapperFactoryBeanClass;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setBeanName(String name) {
    this.beanName = name;
  }

  /**
   * Gets beanNameGenerator to be used while running the scanner.
   *
   * @return the beanNameGenerator BeanNameGenerator that has been configured
   * @since 1.2.0
   */
  public BeanNameGenerator getNameGenerator() {
    return nameGenerator;
  }

  /**
   * Sets beanNameGenerator to be used while running the scanner.
   *
   * @param nameGenerator
   *          the beanNameGenerator to set
   * @since 1.2.0
   */
  public void setNameGenerator(BeanNameGenerator nameGenerator) {
    this.nameGenerator = nameGenerator;
  }

  /**
   * Sets the default scope of scanned mappers.
   * <p>
   * Default is {@code null} (equiv to singleton).
   * </p>
   *
   * @param defaultScope
   *          the default scope
   * @since 2.0.6
   */
  public void setDefaultScope(String defaultScope) {
    this.defaultScope = defaultScope;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void afterPropertiesSet() throws Exception {
    // 检查basePackage是否存在
    notNull(this.basePackage, "Property 'basePackage' is required");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    // left intentionally blank
  }

  /**
   * {@inheritDoc}
   *
   * @since 1.0.2
   */
  @Override
  public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
    // ❗️❗️❗️❗️❗️❗️
    // 执行时机:
    // 	BeanDefinitionRegistryPostProcessor处理器有postProcessBeanDefinitionRegistry()和postProcessBeanFactory()方法
    //	表明其既可以自定义BeanDefinition并注册进容器中,也可以对beanFactory的修改
    //		那为什么逻辑要先执行postProcessBeanDefinitionRegistry然后在执行postProcessBeanFactory呢？
    //		{先执行的证明} -> PostProcessorRegistrationDelegate#invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) 源代码
    //	答案: 因为postProcessBeanDefinitionRegistry是用来创建bean定义的，而postProcessBeanFactory是修改BeanFactory，
    //	当然postProcessBeanFactory也可以修改bean定义的。为了保证在修改之前所有的bean定义的都存在，所以优先执行postProcessBeanDefinitionRegistry。
    //	如不是以上顺序，会出先再修改某个bean定义的报错，因为此bean定义的还没有被创建

    // 1. 对属性进行占位符解析 ~~ 忽略
    if (this.processPropertyPlaceHolders) {
      processPropertyPlaceHolders();
    }

    ClassPathMapperScanner scanner = new ClassPathMapperScanner(registry);
    scanner.setAddToConfig(this.addToConfig);
    scanner.setAnnotationClass(this.annotationClass); // 待扫描的类上必须拥有的注解
    scanner.setMarkerInterface(this.markerInterface); // 待扫描的类上必须实现的接口
    scanner.setSqlSessionFactory(this.sqlSessionFactory); // 已弃用
    scanner.setSqlSessionTemplate(this.sqlSessionTemplate); // 已弃用
    scanner.setSqlSessionFactoryBeanName(this.sqlSessionFactoryBeanName); // 多个SqlSessionFactory存在ioc容器时,指定使用的SqlSessionFactory的beanName
    scanner.setSqlSessionTemplateBeanName(this.sqlSessionTemplateBeanName); // 多个SqlSessionTemplate存在ioc容器时,指定使用的SqlSessionTemplate的beanName
    scanner.setResourceLoader(this.applicationContext);
    scanner.setBeanNameGenerator(this.nameGenerator); // 命名器: 如何决定mapper接口的名字
    scanner.setMapperFactoryBeanClass(this.mapperFactoryBeanClass);
    if (StringUtils.hasText(lazyInitialization)) {
      scanner.setLazyInitialization(Boolean.valueOf(lazyInitialization));
    }
    if (StringUtils.hasText(defaultScope)) {
      scanner.setDefaultScope(defaultScope);
    }

    // 2. ❗️❗️❗️ 向scanner中注册过滤器
    // a: 扫描有实现指定markerInterface的类
    // b: 扫描有指定注解annotationClass的类
    // c: a和b都没有生效时默认扫描basePackage下所有的接口和类
    scanner.registerFilters();
    // 3. 调用scan方法
    // 注意这个:scan()方法是Spring框架提供的ClassPathBeanDefinitionScanner
    scanner.scan(
        StringUtils.tokenizeToStringArray(this.basePackage, ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS));
  }

  /*
   * BeanDefinitionRegistries are called early in application startup, before BeanFactoryPostProcessors. This means that
   * PropertyResourceConfigurers will not have been loaded and any property substitution of this class' properties will
   * fail. To avoid this, find any PropertyResourceConfigurers defined in the context and run them on this class' bean
   * definition. Then update the values.
   */
  private void processPropertyPlaceHolders() {

    // 0. BeanDefinitionRegistries 在应用程序启动的早期被调用，在 BeanFactoryPostProcessors 之前。
    // 这意味着不会加载 PropertyResourceConfigurers，并且此类属性的任何属性替换都将失败。
    // 为避免这种情况，请找到ioc中定义的任何 PropertyResourceConfigurers 并实例化他们 -> 提前实例化
    Map<String, PropertyResourceConfigurer> prcs = applicationContext.getBeansOfType(PropertyResourceConfigurer.class, false, false);

    if (!prcs.isEmpty() && applicationContext instanceof ConfigurableApplicationContext) {
      // 1. 获取MapperScannerConfigurer当前类的BeanDefinition信息
      BeanDefinition mapperScannerBean = ((ConfigurableApplicationContext) applicationContext).getBeanFactory().getBeanDefinition(beanName);

      // PropertyResourceConfigurer does not expose any methods to explicitly perform
      // property placeholder substitution. Instead, create a BeanFactory that just
      // contains this mapper scanner and post process the factory.
      DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
      factory.registerBeanDefinition(beanName, mapperScannerBean);

      for (PropertyResourceConfigurer prc : prcs.values()) {
        prc.postProcessBeanFactory(factory);
      }

      PropertyValues values = mapperScannerBean.getPropertyValues();

      // 2. 获取下面几个属性的表达式 --
      this.basePackage = getPropertyValue("basePackage", values);
      this.sqlSessionFactoryBeanName = getPropertyValue("sqlSessionFactoryBeanName", values);
      this.sqlSessionTemplateBeanName = getPropertyValue("sqlSessionTemplateBeanName", values);
      this.lazyInitialization = getPropertyValue("lazyInitialization", values);
      this.defaultScope = getPropertyValue("defaultScope", values);
    }
    // 3. Optional.ofNullable(xx).map(it->{}).orElse() -> 作用: 如果xx不为null值,那么map()中的lambda表达式生效,如果xx为null值,则map不生效,就通过orElse()生成一个默认值吧
    // map()中的函数就是用来解析占位符表达式的哦

    this.basePackage = Optional.ofNullable(this.basePackage).map(getEnvironment()::resolvePlaceholders).orElse(null);
    this.sqlSessionFactoryBeanName = Optional.ofNullable(this.sqlSessionFactoryBeanName).map(getEnvironment()::resolvePlaceholders).orElse(null);
    this.sqlSessionTemplateBeanName = Optional.ofNullable(this.sqlSessionTemplateBeanName).map(getEnvironment()::resolvePlaceholders).orElse(null);
    this.lazyInitialization = Optional.ofNullable(this.lazyInitialization).map(getEnvironment()::resolvePlaceholders).orElse(null);
    this.defaultScope = Optional.ofNullable(this.defaultScope).map(getEnvironment()::resolvePlaceholders).orElse(null);
  }

  private Environment getEnvironment() {
    return this.applicationContext.getEnvironment();
  }

  private String getPropertyValue(String propertyName, PropertyValues values) {
    // 从PropertyValues中获取指定propertyName的属性

    PropertyValue property = values.getPropertyValue(propertyName);

    if (property == null) {
      return null;
    }

    Object value = property.getValue();

    if (value == null) {
      return null;
    } else if (value instanceof String) {
      return value.toString();
    } else if (value instanceof TypedStringValue) {
      return ((TypedStringValue) value).getValue();
    } else {
      return null;
    }
  }

}
