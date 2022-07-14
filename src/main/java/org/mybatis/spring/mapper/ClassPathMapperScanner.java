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
package org.mybatis.spring.mapper;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.logging.Logger;
import org.mybatis.logging.LoggerFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.scope.ScopedProxyFactoryBean;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.StringUtils;

/**
 * A {@link ClassPathBeanDefinitionScanner} that registers Mappers by {@code basePackage}, {@code annotationClass}, or
 * {@code markerInterface}. If an {@code annotationClass} and/or {@code markerInterface} is specified, only the
 * specified types will be searched (searching for all interfaces will be disabled).
 * <p>
 * This functionality was previously a private class of {@link MapperScannerConfigurer}, but was broken out in version
 * 1.2.0.
 *
 * @author Hunter Presnall
 * @author Eduardo Macarron
 *
 * @see MapperFactoryBean
 * @since 1.2.0
 */
public class ClassPathMapperScanner extends ClassPathBeanDefinitionScanner {
  // 注意: ClassPathMapperScanner 是基于 ClassPathBeanDefinitionScanner 的哦
  // 作用: ClassPathMapperScanner = 在类路径下ClassPath扫描Mapper接口的扫描器Scanner

  private static final Logger LOGGER = LoggerFactory.getLogger(ClassPathMapperScanner.class);

  // Copy of FactoryBean#OBJECT_TYPE_ATTRIBUTE which was added in Spring 5.2
  static final String FACTORY_BEAN_OBJECT_TYPE = "factoryBeanObjectType";

  // 默认就是true ~~ 99%的情况也是true
  // 即将扫描的所有Mapper接口到注入到mybatis的Configuration中去
  private boolean addToConfig = true;

  // 表示是否将扫描到的mapper bean都进行延迟初始化
  // 和@MapperScan的lazyInitialization有关
  private boolean lazyInitialization;

  // 已弃用
  private SqlSessionFactory sqlSessionFactory;

  // 已弃用
  private SqlSessionTemplate sqlSessionTemplate;

  // 指定在 spring 上下文中存在多个 SqlSessionFactory 的情况下使用哪个SqlSessionFactory 。通常只有当您有多个数据源时才需要这样做。
  // 和@MapperScan的sqlSessionTemplateRef值有关
  private String sqlSessionTemplateBeanName;

  // 指定在 spring 上下文中存在多个的情况下使用哪个SqlSessionTemplate 。通常只有当您有多个数据源时才需要这样做。
  // 和@MapperScan的sqlSessionFactoryRef值有关
  private String sqlSessionFactoryBeanName;

  // 基础包下需要过滤扫描的注解
  // 和@MapperScan的annotationClass有关
  private Class<? extends Annotation> annotationClass;

  // 基础包下需要过滤扫描的超类或接口
  // 和@MapperScan的markerInterface有关
  private Class<?> markerInterface;

  // ❗️❗️❗️ 最终Mapper接口的BeanDefinition的beanClass字段会被ClassPathMapperScanner替换为MapperFactoryBean
  // 默认情况下就是: MapperFactoryBean -- 用户可以通过继承MapperFactoryBean然后通过@MapperScan的factoryBean指定过来
  private Class<? extends MapperFactoryBean> mapperFactoryBeanClass = MapperFactoryBean.class;

  // 定义扫描的mapper bean的范围scope
  // 和@MapperScan的defaultScope有关 -- 比如单例或者原型
  private String defaultScope;

  public ClassPathMapperScanner(BeanDefinitionRegistry registry) {
    super(registry, false);
  }

  public void setAddToConfig(boolean addToConfig) {
    this.addToConfig = addToConfig;
  }

  public void setAnnotationClass(Class<? extends Annotation> annotationClass) {
    this.annotationClass = annotationClass;
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
  public void setLazyInitialization(boolean lazyInitialization) {
    this.lazyInitialization = lazyInitialization;
  }

  public void setMarkerInterface(Class<?> markerInterface) {
    this.markerInterface = markerInterface;
  }

  public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }

  public void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
    this.sqlSessionTemplate = sqlSessionTemplate;
  }

  public void setSqlSessionTemplateBeanName(String sqlSessionTemplateBeanName) {
    this.sqlSessionTemplateBeanName = sqlSessionTemplateBeanName;
  }

  public void setSqlSessionFactoryBeanName(String sqlSessionFactoryBeanName) {
    this.sqlSessionFactoryBeanName = sqlSessionFactoryBeanName;
  }

  /**
   * @deprecated Since 2.0.1, Please use the {@link #setMapperFactoryBeanClass(Class)}.
   */
  @Deprecated
  public void setMapperFactoryBean(MapperFactoryBean<?> mapperFactoryBean) {
    this.mapperFactoryBeanClass = mapperFactoryBean == null ? MapperFactoryBean.class : mapperFactoryBean.getClass();
  }

  /**
   * Set the {@code MapperFactoryBean} class.
   *
   * @param mapperFactoryBeanClass
   *          the {@code MapperFactoryBean} class
   * @since 2.0.1
   */
  public void setMapperFactoryBeanClass(Class<? extends MapperFactoryBean> mapperFactoryBeanClass) {
    this.mapperFactoryBeanClass = mapperFactoryBeanClass == null ? MapperFactoryBean.class : mapperFactoryBeanClass;
  }

  /**
   * Set the default scope of scanned mappers.
   * <p>
   * Default is {@code null} (equiv to singleton).
   * </p>
   *
   * @param defaultScope
   *          the scope
   * @since 2.0.6
   */
  public void setDefaultScope(String defaultScope) {
    this.defaultScope = defaultScope;
  }

  /**
   * Configures parent scanner to search for the right interfaces. It can search for all interfaces or just for those
   * that extends a markerInterface or/and those annotated with the annotationClass
   */
  public void registerFilters() {
    // ❗️❗️❗️
    // 利用超类 ClassPathBeanDefinitionScanner 的扫描能力
    // 向其添加 includeFilter/excludeFilter

    // 0. 默认情况 -- 没有指定annotationClass/markerInterface时默认就是去扫描basePackage下面的所有类和接口
    // acceptAllInterfaces 就是标志位: 表示
    boolean acceptAllInterfaces = true;

    // 1. 如果有指定要扫描的注解:annotationClass -- 则为这个注解添加一个扫描器
    // 比如指定@Mapper,那么就会在basePackage下扫描并过滤出所有带有@Mapper注解的类
    if (this.annotationClass != null) {
      addIncludeFilter(new AnnotationTypeFilter(this.annotationClass));
      acceptAllInterfaces = false;
    }

    // 2. 如果有指定所有Mapper接口的超类,即markerInterface不为空,
    // 比如markerInterface为BaseMapper,那么就会在basePackage下扫描并过滤出BaseMapper的子类
    if (this.markerInterface != null) {
      addIncludeFilter(new AssignableTypeFilter(this.markerInterface) {
        @Override
        protected boolean matchClassName(String className) {
          return false;
        }
      });
      acceptAllInterfaces = false;
    }

    // 3. 没有指定annotationClass/markerInterface时默认就是去扫描basePackage下面的所有接口
    if (acceptAllInterfaces) {
      // 默认包含接受所有类的过滤器
      addIncludeFilter((metadataReader, metadataReaderFactory) -> true);
    }

    // 4. 排除 package-info.java
    addExcludeFilter((metadataReader, metadataReaderFactory) -> {
      String className = metadataReader.getClassMetadata().getClassName();
      return className.endsWith("package-info");
    });
  }

  /**
   * Calls the parent search that will search and register all the candidates. Then the registered objects are post
   * processed to set them as MapperFactoryBeans
   */
  @Override
  public Set<BeanDefinitionHolder> doScan(String... basePackages) {
    // 注意: ❗️❗️❗️ 重写了doScan()方法哦

    // ❗️❗️❗️ 被扫描的对象都直接加入到了BeanDefinitionRegistry中哦
    // 1. 执行超类的doScan() -> 将通过IncludeFilter或ExcludeFilter过滤出来的类将被创建BeanDefinition并加入到BeanDefinitionRegistry中
    // 当然对于ClassPathMapperScanner,扫描出来的应该都是Mapper接口
    // 讲解:
    //  1.通过注册的includeFilter和excludeFilter找到合适的Class
    //  2.被注入的BeanDefinition都是ScannedGenericBeanDefinition类型的,BeanDefinition的source属性即源就是.class文件的Resource
    //  3.使用当前类的BeanNameGenerator为这个BeanDefinition设置beanName哦
    //     a: 检查类上是否有@Scope注解,有的话,去获取其@Scope的value属性作为Scope的name,@Scope的proxyMode属性作为代理模式[只要非ScopedProxyMode.DEFAULT]
    //     b: 将上面的ScopeMetadata设置到BeanDefinition的scope属性上面去
    //  4.可以处理扫描类上的@Lazy @Primary @Role @Description 等注解解析
    //  5.检查注册表中是否有同名的beanName,且两个BeanDefinition不兼容的,有的话报出异常
    //  6.检查是否需要为其创建ScopedProxyMode
    //    6.1 如果滴3.b步骤生成的ScopeMetaData中的ScopeProxyMode非ScopedProxyMode.NO,而是TARGET_CLASS
    //        就将BeanDefinition改造为 -> RootBeanDefinition(ScopedProxyFactoryBean.class)
    //          6.1.a 新的代理的BeanDefinition为"scopedTarget." + originalBeanName
    //          6.1.b 设置代理后BeanDefinition的beanClass为ScopedProxyFactoryBean/resource/source/role
    //  7.注册到BeanDefinitionRegistry中去
    Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);

    // 2.1 如果没有扫描出来任何Mapper接口,记录一个警告
    if (beanDefinitions.isEmpty()) {
      LOGGER.warn(() -> "No MyBatis mapper was found in '" + Arrays.toString(basePackages)
          + "' package. Please check your configuration.");
    }
    // 2.2 否则 -- 开始处理已经注册了的Mapper接口的BeanDefinition
    else {
      processBeanDefinitions(beanDefinitions);
    }

    return beanDefinitions;
  }

  private void processBeanDefinitions(Set<BeanDefinitionHolder> beanDefinitions) {
    // 将前面注册过的beanDefinition给修改掉
    // 注意: BeanDefinitionHolder 中持有的 BeanDefinition 已经被注入到 ioc 容器中啦 -> 这里是对已注册的BeanDefinition进行修改罢了

    AbstractBeanDefinition definition;
    BeanDefinitionRegistry registry = getRegistry();
    // 1. 开始遍历所有注册的Mapper接口的BeanDefinition
    for (BeanDefinitionHolder holder : beanDefinitions) {
      definition = (AbstractBeanDefinition) holder.getBeanDefinition();
      boolean scopedProxy = false;
      // 1.1 ❗️❗️❗️
      // 对于Mapper接口上有@Scope(proxyMode=ScopeProxyMode.TARGET_CLASS\INTERFACES) -- 会在超类的scan()操作中处理@Scope,创建代理的BeanDefinition出来哦
      // 因此需要从DecoratedDefinition获取原始Mapper接口定义的BeanDefinition
      if (ScopedProxyFactoryBean.class.getName().equals(definition.getBeanClassName())) {
        definition = (AbstractBeanDefinition) Optional
            .ofNullable(((RootBeanDefinition) definition).getDecoratedDefinition())
            .map(BeanDefinitionHolder::getBeanDefinition).orElseThrow(() -> new IllegalStateException(
                "The target bean definition of scoped proxy bean not found. Root bean definition[" + holder + "]"));
        scopedProxy = true;
      }
      String beanClassName = definition.getBeanClassName();
      LOGGER.debug(() -> "Creating MapperFactoryBean with name '" + holder.getBeanName() + "' and '" + beanClassName + "' mapperInterface");

      // 1.2 ❗️❗️❗️❗️❗️❗️❗️❗️❗️❗️❗️❗️❗️❗️❗️❗️❗️❗️
      // 当前BeanDefinition的beanClass是Mapper接口的Class
      // 但在Mybatis中实际mapper接口的实际类应该是MapperProxyFactory.newInstance()为Mapper接口创建的代理类
      // 但是spring无法直接来调用MapperProxyFactory.newInstance()生成代理对象,因此 ibatis-spring 添加了一个 MapperFactoryBean 为其引入 Mapper接口的实现
      // 所以后面将 definition.setBeanClass(this.mapperFactoryBeanClass);
      // 而这里就是调用 MapperFactoryBean 下面的构造器进行实例化:
      // public MapperFactoryBean(Class<T> mapperInterface) {
      //    this.mapperInterface = mapperInterface; // 传入实际的Mapper接口的Class
      //  }
      definition.getConstructorArgumentValues().addGenericArgumentValue(beanClassName); // issue #59

      // 1.3 设置 MapperFactoryBean 的 mapperInterface 与 addToConfig属性值
      try {
        definition.getPropertyValues().add("mapperInterface", Resources.classForName(beanClassName));
      } catch (ClassNotFoundException ignore) {
      }

      // 1.4 ❗❗️❗️❗️ 这里将替换调原来Mapper接口的beanClass,替换为mapperFactoryBeanClass
      // 将设置beanDefinition的实际beanClass替换为: MapperFactoryBean ->
      definition.setBeanClass(this.mapperFactoryBeanClass);

      // 1.5 设置MapperFactoryBean的addToConfig属性 -> 99%的情况addToConfig都是true
      definition.getPropertyValues().add("addToConfig", this.addToConfig);

      // 1.6 向BeanDefinition设置一个属性: key="factoryBeanObjectType",value=实际Mapper接口的class
      definition.setAttribute(FACTORY_BEAN_OBJECT_TYPE, beanClassName);

      // 1.4 指定 MapperFactoryBean 的 sqlSessionFactory 属性 -- 允许为空
      boolean explicitFactoryUsed = false;
      if (StringUtils.hasText(this.sqlSessionFactoryBeanName)) {
        definition.getPropertyValues().add("sqlSessionFactory", new RuntimeBeanReference(this.sqlSessionFactoryBeanName));
        explicitFactoryUsed = true;
      } else if (this.sqlSessionFactory != null) {
        definition.getPropertyValues().add("sqlSessionFactory", this.sqlSessionFactory);
        explicitFactoryUsed = true;
      }

      // 1.5 指定  MapperFactoryBean 的 sqlSessionTemplate 属性 -- 允许为空
      // ❗️❗️❗️ note: 当sqlSessionFactory生效时无法注入sqlSessionTemplate
      if (StringUtils.hasText(this.sqlSessionTemplateBeanName)) {
        if (explicitFactoryUsed) {
          LOGGER.warn(
              () -> "Cannot use both: sqlSessionTemplate and sqlSessionFactory together. sqlSessionFactory is ignored.");
        }
        definition.getPropertyValues().add("sqlSessionTemplate", new RuntimeBeanReference(this.sqlSessionTemplateBeanName));
        explicitFactoryUsed = true;
      } else if (this.sqlSessionTemplate != null) {
        if (explicitFactoryUsed) {
          LOGGER.warn(
              () -> "Cannot use both: sqlSessionTemplate and sqlSessionFactory together. sqlSessionFactory is ignored.");
        }
        definition.getPropertyValues().add("sqlSessionTemplate", this.sqlSessionTemplate);
        explicitFactoryUsed = true;
      }

      // 1.6 ❗️❗️❗️
      // 未指定sqlSessionFactory且未指定sqlSessionTemplate
      // -> MapperFactoryBean的超类SqlSessionDaoSupport#checkDaoConfig()要求: 必须有SqlSessionTemplate或能够产生SqlSessionTemplate的SqlSessionFactory [否则:报错哦]
      // ->
      if (!explicitFactoryUsed) {
        LOGGER.debug(() -> "Enabling autowire by type for MapperFactoryBean with name '" + holder.getBeanName() + "'.");
        definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE); // 按照类型填充 -- 当没有指定sqlSessionTemplate或sqlSessionFactory
      }

      // 1.7 设置懒加载是否 -> 取决于@MapperScan的lazyInitialization属性
      definition.setLazyInit(lazyInitialization);

      // 1.8 如果mapper接口被@Scope代理过,就不执行后面的操作
      if (scopedProxy) {
        continue;
      }

      // 1.9 Mapper接口的scope是单例的,并且默认的Scope非空,就更新definition的scope为defaultScope
      // defaultScope取决于@MapperScan的defaultScope属性哦
      if (ConfigurableBeanFactory.SCOPE_SINGLETON.equals(definition.getScope()) && defaultScope != null) {
        definition.setScope(defaultScope);
      }

      // 1.10 扫描到的Mapper接口的BeanDefinition非单例模式 ~~ 可忽略,99%都是单例的
      if (!definition.isSingleton()) {
        BeanDefinitionHolder proxyHolder = ScopedProxyUtils.createScopedProxy(holder, registry, true);
        if (registry.containsBeanDefinition(proxyHolder.getBeanName())) {
          registry.removeBeanDefinition(proxyHolder.getBeanName());
        }
        registry.registerBeanDefinition(proxyHolder.getBeanName(), proxyHolder.getBeanDefinition());
      }

    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
    // ❗️❗️❗️
    // note: 重写了isCandidateComponent(AnnotatedBeanDefinition beanDefinition) -> 要求必须是接口哦,并且是独立的类
    return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected boolean checkCandidate(String beanName, BeanDefinition beanDefinition) {
    // ❗️❗️❗️
    // note: 重写了checkCandidate(String beanName, BeanDefinition beanDefinition) -> 用于检查生成的BeanDefinition是否可以注入
    // 没啥特别: 只是检查不合格后,加入一条日志而已
    if (super.checkCandidate(beanName, beanDefinition)) {
      return true;
    } else {
      LOGGER.warn(() -> "Skipping MapperFactoryBean with name '" + beanName + "' and '"
          + beanDefinition.getBeanClassName() + "' mapperInterface" + ". Bean already defined with the same name!");
      return false;
    }
  }

}
