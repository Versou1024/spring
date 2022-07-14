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

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.Configuration;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.support.SqlSessionDaoSupport;
import org.springframework.beans.factory.FactoryBean;

/**
 * BeanFactory that enables injection of MyBatis mapper interfaces. It can be set up with a SqlSessionFactory or a
 * pre-configured SqlSessionTemplate.
 * <p>
 * Sample configuration:
 *
 * <pre class="code">
 * {@code
 *   <bean id="baseMapper" class="org.mybatis.spring.mapper.MapperFactoryBean" abstract="true" lazy-init="true">
 *     <property name="sqlSessionFactory" ref="sqlSessionFactory" />
 *   </bean>
 *
 *   <bean id="oneMapper" parent="baseMapper">
 *     <property name="mapperInterface" value="my.package.MyMapperInterface" />
 *   </bean>
 *
 *   <bean id="anotherMapper" parent="baseMapper">
 *     <property name="mapperInterface" value="my.package.MyAnotherMapperInterface" />
 *   </bean>
 * }
 * </pre>
 * <p>
 * Note that this factory can only inject <em>interfaces</em>, not concrete classes.
 *
 * @author Eduardo Macarron
 * @see SqlSessionTemplate
 */
public class MapperFactoryBean<T> extends SqlSessionDaoSupport implements FactoryBean<T> {
  // 命名:
  // Mapper FactoryBean = Mapper接口的MapperFactoryBean
  // 请注意，这个工厂只能注入Mapper接口，不能注入具体类 -> 通过调用吗mybatis的MapperProxyFactory.newInstance()为Mapper接口创建的代理类

  // 设置扫描到的实际的Mybatis的mapper接口
  private Class<T> mapperInterface;

  // 如果 addToConfig 为 false，则mapper不会被添加到MyBatis。这意味着它必须已经包含在mybatis-config.xml 中。
  // 如果为真，则映射器将在尚未注册的情况下添加到 MyBatis -- 99%的情况都是true
  private boolean addToConfig = true;

  public MapperFactoryBean() {
    // intentionally empty
  }

  public MapperFactoryBean(Class<T> mapperInterface) {
    // 被ioc容器调用的构造器哦
    this.mapperInterface = mapperInterface;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void checkDaoConfig() {
    // ❗️❗️❗️
    // 在超类SqlSessionDaoSupport#checkDaoConfig()的基础上 -- 继续扩展处理
    // DaoSupport 是 Spring 提供给 DAO 进行初始化的一个机会[因为 DaoSupport implements InitializingBean]

    // 1. 调用super.checkDaoConfig()
    super.checkDaoConfig();

    notNull(this.mapperInterface, "Property 'mapperInterface' is required");

    // 2. 获取Mybatis唯一的Configuration
    Configuration configuration = getSqlSession().getConfiguration();
    // 3. 检查是否有对应的mapper接口
    if (this.addToConfig && !configuration.hasMapper(this.mapperInterface)) {
      try {
        // 3.1 ❗️❗️❗️
        // 从sqlSession中拿出Configuration中调用addMapper方法 -> 然后再后续的getObject()->从sqlSession中获取就没有问题啦
        // note️ -- 但是请注意: 这要求mapper.xml和mapper接口在同一个目录中,否则是无法找到的哦 -> 无法找到xml的情况下,就只会去解析注解信息
        // mybatis-plus会提供额外的能力哦

        // 简述流程: 假设 mapperInterface 是 com.sdk.developer.UserMapper.class
        // 1. configuration.addMapper() -> 实际调用 mapperRegistry.getMapper(type, sqlSession) [因此必须传递用户必须创建SqlSession或SqlSessionFactory]
        // 2. 加入到MapperRegistry的缓存knownMappers中,其中缓存的value就是new MapperProxyFactory<>(type) [后续去getMapper将从这个Value中获取Mapper接口代理对象哦]
        // 3. 构建XMLMapperBuilder -> 开始Mapper接口下同名mapper.xml文件 -> 比如 com.sdk.developer.UserMapper 下的/com/sdk/developer/UserMapper.xml [实际都找不到,会在Mybatis-Plus中优化]
        // 4. 构建MapperAnnotationBuilder -> 开始解析Mapper接口下的标有Mybatis注解的方法 -> 相关信息解析出来存入Configuration中
        configuration.addMapper(this.mapperInterface);
      } catch (Exception e) {
        logger.error("Error while adding the mapper '" + this.mapperInterface + "' to configuration.", e);
        throw new IllegalArgumentException(e);
      } finally {
        ErrorContext.instance().reset();
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public T getObject() throws Exception {
    // ❗️❗️❗️
    // 使用了Spring提供的FactoryBean接口
    // 结合 mybatis 的 SqlSession.getMapper()方法

    // 简述:
    // 由于在checkDaoConfig()是由InitializingBean#afterPropertiesSet()初始化方法中完成的
    // 因此已经执行过configuration.addMapper(this.mapperInterface) -> Mapper接口的注解信息和同文件下的Mapper.xml已经解析过
    // 这里执行: getSqlSession().getMapper(this.mapperInterface)
    // 触发:
    // 1. MapperProxyFactory<T>.newInstance(sqlSession)
    // 2. Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[] { mapperInterface }, mapperProxy) -> 重点就是在于MapperProxy的拦截能力
    // 3. MapperProxy#invoke()方法中
    //    3.1 Object中的方法直接执行
    //    3.2 Mapper接口中使用Default修饰的方法,直接执行
    //    3.3 其余方法 -> 通过 new MapperMethod(mapperInterface, method, sqlSession.getConfiguration()).execute() 来执行 [这里面的逻辑就更加复杂啦(不做过多参数可详见Mybatis的源码分析哦)]
    return getSqlSession().getMapper(this.mapperInterface);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Class<T> getObjectType() {
    // ❗️❗️❗️
    // 对应的用户@Autowrite实际上还是mapper接口的class
    // 即这里的mapperInterface
    return this.mapperInterface;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSingleton() {
    return true;
  }

  // ------------- mutators --------------

  /**
   * Sets the mapper interface of the MyBatis mapper
   *
   * @param mapperInterface class of the interface
   */
  public void setMapperInterface(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  /**
   * Return the mapper interface of the MyBatis mapper
   *
   * @return class of the interface
   */
  public Class<T> getMapperInterface() {
    return mapperInterface;
  }

  /**
   * If addToConfig is false the mapper will not be added to MyBatis. This means it must have been included in
   * mybatis-config.xml.
   * <p>
   * If it is true, the mapper will be added to MyBatis in the case it is not already registered.
   * <p>
   * By default addToConfig is true.
   *
   * @param addToConfig a flag that whether add mapper to MyBatis or not
   */
  public void setAddToConfig(boolean addToConfig) {
    this.addToConfig = addToConfig;
  }

  /**
   * Return the flag for addition into MyBatis config.
   *
   * @return true if the mapper will be added to MyBatis in the case it is not already registered.
   */
  public boolean isAddToConfig() {
    return addToConfig;
  }
}
