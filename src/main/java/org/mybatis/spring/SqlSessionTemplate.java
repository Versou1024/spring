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
package org.mybatis.spring;

import static java.lang.reflect.Proxy.newProxyInstance;

import static org.apache.ibatis.reflection.ExceptionUtil.unwrapThrowable;
import static org.mybatis.spring.SqlSessionUtils.closeSqlSession;
import static org.mybatis.spring.SqlSessionUtils.getSqlSession;
import static org.mybatis.spring.SqlSessionUtils.isSqlSessionTransactional;
import static org.springframework.util.Assert.notNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.dao.support.PersistenceExceptionTranslator;

/**
 * Thread safe, Spring managed, {@code SqlSession} that works with Spring transaction management to ensure that that the
 * actual SqlSession used is the one associated with the current Spring transaction. In addition, it manages the session
 * life-cycle, including closing, committing or rolling back the session as necessary based on the Spring transaction
 * configuration.
 * <p>
 * The template needs a SqlSessionFactory to create SqlSessions, passed as a constructor argument. It also can be
 * constructed indicating the executor type to be used, if not, the default executor type, defined in the session
 * factory will be used.
 * <p>
 * This template converts MyBatis PersistenceExceptions into unchecked DataAccessExceptions, using, by default, a
 * {@code MyBatisExceptionTranslator}.
 * <p>
 * Because SqlSessionTemplate is thread safe, a single instance can be shared by all DAOs; there should also be a small
 * memory savings by doing this. This pattern can be used in Spring configuration files as follows:
 *
 * <pre class="code">
 * {@code
 * <bean id="sqlSessionTemplate" class="org.mybatis.spring.SqlSessionTemplate">
 *   <constructor-arg ref="sqlSessionFactory" />
 * </bean>
 * }
 * </pre>
 *
 * @author Putthiphong Boonphong
 * @author Hunter Presnall
 * @author Eduardo Macarron
 *
 * @see SqlSessionFactory
 * @see MyBatisExceptionTranslator
 */
public class SqlSessionTemplate implements SqlSession, DisposableBean {
  // 位于: org.mybatis.spring package 下

  // 作用:
  // 1. 单纯的SqlSession其关闭操作需要人为控制,而SqlSessionTemplate同时实现了DisposableBean,将SqlSession的销毁交给Spring管理
  // 2. 构造的 SqlSession 是受到 Spring 管理的 -> 本质也就是还是一个Mybatis的SqlSession

  // 继承体系:
  // SqlSessionTemplate implements SqlSession

  // 来自:Mybatis的创建SqlSession的工厂
  // 常用的就是 -- DefaultSqlSessionFactory类
  private final SqlSessionFactory sqlSessionFactory;

  // 来自:Mybatis的执行器类型
  // 执行器类型: 包括 SIMPLE\REUSE\BATCH
  // 默认: SIMPLE
  private final ExecutorType executorType;

  // 来自:Mybatis的SqlSession
  // -- 装饰和代理模式
  // 实际SqlSession的核心功能委托给sqlSessionProxy处理
  // 而且实际上在构造器上我们还对目标SqlSession进行了代理哦
  private final SqlSession sqlSessionProxy;

  // 持久异常翻译器 -> 来自Spring
  // 作用: 将ORM矿建报出的异常,翻译为Spring框架合适的异常,其超类时Spring提供的DataAccessException异常
  // 默认是: SqlSessionInterceptor
  private final PersistenceExceptionTranslator exceptionTranslator;

  // 实际项目: 如何引入的SqlSessionTemplate
  // 以 xy-website 项目为例:
  // public class PrimaryDataSourceConfig {
  //
  //    private final MybatisPlusInterceptor mybatisPlusInterceptor;
  //
  //    public PrimaryDataSourceConfig(MybatisPlusInterceptor mybatisPlusInterceptor) {
  //        this.mybatisPlusInterceptor = mybatisPlusInterceptor;
  //    }
  //
  //    /**
  //     * ConfigurationProperties将配置文件的匹配的属性按名注入到DataSource类中
  //     */
  //    @Bean(name = "primaryDataSource")
  //    @ConfigurationProperties(prefix = "spring.datasource.dynamic.datasource.primary")
  //    @Primary
  //    public DataSource createDataSource() {
  //        return DataSourceBuilder.create().build();
  //    }
  //
  //    @Bean(name = "primarySqlSessionFactory")
  //    @Primary
  //    public SqlSessionFactory sessionFactory(@Qualifier("primaryDataSource") DataSource dataSource) throws Exception {
  //
  //        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
  //        bean.setDataSource(dataSource);
  //        //设置分页插件
  //        bean.setPlugins(mybatisPlusInterceptor);
  //        //多数据源指定对应枚举包名
  //        bean.setTypeEnumsPackage("com.xylink.website.**.constant");
  //        bean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));
  //        return bean.getObject();
  //    }
  //
  //    @Bean(name = "primaryTransactionManager")
  //    @Primary
  //    public DataSourceTransactionManager transactionManager(@Qualifier("primaryDataSource") DataSource dataSource) {
  //        return new DataSourceTransactionManager(dataSource);
  //    }
  //
  //    @Bean(name = "primarySqlSessionTemplate")
  //    @Primary
  //    public SqlSessionTemplate sqlSessionTemplate(@Qualifier("primarySqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
  //        return new SqlSessionTemplate(sqlSessionFactory);
  //    }
  //
  //}

  // note: 在mybatis-plus项目中,用户可以不再手动引入SqlSessionTemplate
  // 在mybatis-plus项目的 MybatisPlusAutoConfiguration#sqlSessionTemplate(..) 方法中会构造 SqlSessionTemplate

  /**
   * Constructs a Spring managed SqlSession with the {@code SqlSessionFactory} provided as an argument.
   *
   * @param sqlSessionFactory
   *          a factory of SqlSession
   */
  public SqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
    // sqlSessionFactory.getConfiguration().getDefaultExecutorType() 默认是SIMPLE的 [99%的情况使用这个,以上面的项目为例哦]
    this(sqlSessionFactory, sqlSessionFactory.getConfiguration().getDefaultExecutorType());
  }

  /**
   * Constructs a Spring managed SqlSession with the {@code SqlSessionFactory} provided as an argument and the given
   * {@code ExecutorType} {@code ExecutorType} cannot be changed once the {@code SqlSessionTemplate} is constructed.
   *
   * @param sqlSessionFactory
   *          a factory of SqlSession
   * @param executorType
   *          an executor type on session
   */
  public SqlSessionTemplate(SqlSessionFactory sqlSessionFactory, ExecutorType executorType) {
    // 额外指定: ExecutorType
    this(sqlSessionFactory, executorType,
        new MyBatisExceptionTranslator(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(), true));
  }

  /**
   * Constructs a Spring managed {@code SqlSession} with the given {@code SqlSessionFactory} and {@code ExecutorType}. A
   * custom {@code SQLExceptionTranslator} can be provided as an argument so any {@code PersistenceException} thrown by
   * MyBatis can be custom translated to a {@code RuntimeException} The {@code SQLExceptionTranslator} can also be null
   * and thus no exception translation will be done and MyBatis exceptions will be thrown
   *
   * @param sqlSessionFactory
   *          a factory of SqlSession
   * @param executorType
   *          an executor type on session
   * @param exceptionTranslator
   *          a translator of exception
   */
  public SqlSessionTemplate(SqlSessionFactory sqlSessionFactory, ExecutorType executorType,
      PersistenceExceptionTranslator exceptionTranslator) {
    // 额外指定: PersistenceExceptionTranslator ORM框架异常翻译器

    notNull(sqlSessionFactory, "Property 'sqlSessionFactory' is required");
    notNull(executorType, "Property 'executorType' is required");

    this.sqlSessionFactory = sqlSessionFactory;
    this.executorType = executorType;
    this.exceptionTranslator = exceptionTranslator;
    // ❗️❗️❗️
    // 代理类的拦截器 -- 内部类SqlSessionInterceptor
    this.sqlSessionProxy = (SqlSession) newProxyInstance(SqlSessionFactory.class.getClassLoader(), new Class[] { SqlSession.class }, new SqlSessionInterceptor());
  }

  public SqlSessionFactory getSqlSessionFactory() {
    return this.sqlSessionFactory;
  }

  public ExecutorType getExecutorType() {
    return this.executorType;
  }

  public PersistenceExceptionTranslator getPersistenceExceptionTranslator() {
    return this.exceptionTranslator;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T selectOne(String statement) {
    return this.sqlSessionProxy.selectOne(statement);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T selectOne(String statement, Object parameter) {
    return this.sqlSessionProxy.selectOne(statement, parameter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <K, V> Map<K, V> selectMap(String statement, String mapKey) {
    return this.sqlSessionProxy.selectMap(statement, mapKey);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey) {
    return this.sqlSessionProxy.selectMap(statement, parameter, mapKey);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds) {
    return this.sqlSessionProxy.selectMap(statement, parameter, mapKey, rowBounds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> Cursor<T> selectCursor(String statement) {
    return this.sqlSessionProxy.selectCursor(statement);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> Cursor<T> selectCursor(String statement, Object parameter) {
    return this.sqlSessionProxy.selectCursor(statement, parameter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> Cursor<T> selectCursor(String statement, Object parameter, RowBounds rowBounds) {
    return this.sqlSessionProxy.selectCursor(statement, parameter, rowBounds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <E> List<E> selectList(String statement) {
    return this.sqlSessionProxy.selectList(statement);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <E> List<E> selectList(String statement, Object parameter) {
    return this.sqlSessionProxy.selectList(statement, parameter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
    return this.sqlSessionProxy.selectList(statement, parameter, rowBounds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void select(String statement, ResultHandler handler) {
    this.sqlSessionProxy.select(statement, handler);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void select(String statement, Object parameter, ResultHandler handler) {
    this.sqlSessionProxy.select(statement, parameter, handler);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler) {
    this.sqlSessionProxy.select(statement, parameter, rowBounds, handler);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int insert(String statement) {
    return this.sqlSessionProxy.insert(statement);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int insert(String statement, Object parameter) {
    return this.sqlSessionProxy.insert(statement, parameter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int update(String statement) {
    return this.sqlSessionProxy.update(statement);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int update(String statement, Object parameter) {
    return this.sqlSessionProxy.update(statement, parameter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int delete(String statement) {
    return this.sqlSessionProxy.delete(statement);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int delete(String statement, Object parameter) {
    return this.sqlSessionProxy.delete(statement, parameter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T getMapper(Class<T> type) {
    return getConfiguration().getMapper(type, this);
  }

  // ❗️❗️❗️将commit提交/rollback回滚/close关闭三个操作重写 -- 直接抛出异常

  /**
   * {@inheritDoc}
   */
  @Override
  public void commit() {
    throw new UnsupportedOperationException("Manual commit is not allowed over a Spring managed SqlSession");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void commit(boolean force) {
    throw new UnsupportedOperationException("Manual commit is not allowed over a Spring managed SqlSession");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void rollback() {
    throw new UnsupportedOperationException("Manual rollback is not allowed over a Spring managed SqlSession");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void rollback(boolean force) {
    throw new UnsupportedOperationException("Manual rollback is not allowed over a Spring managed SqlSession");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() {
    throw new UnsupportedOperationException("Manual close is not allowed over a Spring managed SqlSession");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void clearCache() {
    this.sqlSessionProxy.clearCache();
  }

  /**
   * {@inheritDoc}
   *
   */
  @Override
  public Configuration getConfiguration() {
    return this.sqlSessionFactory.getConfiguration();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Connection getConnection() {
    return this.sqlSessionProxy.getConnection();
  }

  /**
   * {@inheritDoc}
   *
   * @since 1.0.2
   *
   */
  @Override
  public List<BatchResult> flushStatements() {
    return this.sqlSessionProxy.flushStatements();
  }

  /**
   * Allow gently dispose bean:
   *
   * <pre>
   * {@code
   *
   * <bean id="sqlSession" class="org.mybatis.spring.SqlSessionTemplate">
   *  <constructor-arg index="0" ref="sqlSessionFactory" />
   * </bean>
   * }
   * </pre>
   *
   * The implementation of {@link DisposableBean} forces spring context to use {@link DisposableBean#destroy()} method
   * instead of {@link SqlSessionTemplate#close()} to shutdown gently.
   *
   * @see SqlSessionTemplate#close()
   * @see "org.springframework.beans.factory.support.DisposableBeanAdapter#inferDestroyMethodIfNecessary(Object, RootBeanDefinition)"
   * @see "org.springframework.beans.factory.support.DisposableBeanAdapter#CLOSE_METHOD_NAME"
   */
  @Override
  public void destroy() throws Exception {
    // This method forces spring disposer to avoid call of SqlSessionTemplate.close() which gives
    // UnsupportedOperationException
  }

  /**
   * Proxy needed to route MyBatis method calls to the proper SqlSession got from Spring's Transaction Manager It also
   * unwraps exceptions thrown by {@code Method#invoke(Object, Object...)} to pass a {@code PersistenceException} to the
   * {@code PersistenceExceptionTranslator}.
   */
  private class SqlSessionInterceptor implements InvocationHandler {
    // 代理需要将 MyBatis 的方法调用 路由 到从 Spring 的事务管理器获取的正确 SqlSession
    // 它还解包Method#invoke(Object, Object...)抛出的异常以将PersistenceExceptionTranslator PersistenceException

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      // 1. 每次都是重新去获取一个SqlSession
      // 注意: 静态导入 import static org.mybatis.spring.SqlSessionUtils -> 实际执行 SqlSessionUtils.getSqlSession()
      SqlSession sqlSession = getSqlSession(SqlSessionTemplate.this.sqlSessionFactory, SqlSessionTemplate.this.executorType, SqlSessionTemplate.this.exceptionTranslator);
      try {
        // 2. 执行目标的mapper方法
        Object result = method.invoke(sqlSession, args);
        // 3. 检查SqlSession是否被Spring的事务同步管理器持有 -> 持有表明: 当前Mapper方法在一个事务中执行,不需要自己执行sqlSessiion.commit(),而是交给@Transactional的事务管理器来commit()
        if (!isSqlSessionTransactional(sqlSession, SqlSessionTemplate.this.sqlSessionFactory)) {
          // 3.1 即使在非脏会话上也强制提交，因为某些数据库在调用 close() 之前需要提交回
          sqlSession.commit(true);
        }
        // 3.2 返回结果
        return result;
      } catch (Throwable t) {
        // 4. 捕获到异常 -> 将ORM框架的异常,翻译为Spring的DataAccessException系列异常,并且释放SqlSession

        // 4.1 拿到真实的异常
        Throwable unwrapped = unwrapThrowable(t);
        // 4.2 对于: 持久化异常PersistenceException,需要使用翻译器exceptionTranslator翻译
        // note: PersistenceException是Mybatis的异常超类之一
        if (SqlSessionTemplate.this.exceptionTranslator != null && unwrapped instanceof PersistenceException) {
          // 4.3 出现异常,需要释放SqlSession
          closeSqlSession(sqlSession, SqlSessionTemplate.this.sqlSessionFactory);
          sqlSession = null;
          // 4.4 异常转换
          Throwable translated = SqlSessionTemplate.this.exceptionTranslator.translateExceptionIfPossible((PersistenceException) unwrapped);
          if (translated != null) {
            unwrapped = translated;
          }
        }
        // 4.5 重新抛出转换后的异常
        throw unwrapped;
      } finally {
        if (sqlSession != null) {
          closeSqlSession(sqlSession, SqlSessionTemplate.this.sqlSessionFactory);
        }
      }
    }
  }

}
