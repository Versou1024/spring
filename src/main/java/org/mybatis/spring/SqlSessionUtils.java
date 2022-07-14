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

import static org.springframework.util.Assert.notNull;

import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.logging.Logger;
import org.mybatis.logging.LoggerFactory;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Handles MyBatis SqlSession life cycle. It can register and get SqlSessions from Spring
 * {@code TransactionSynchronizationManager}. Also works if no transaction is active.
 *
 * @author Hunter Presnall
 * @author Eduardo Macarron
 */
public final class SqlSessionUtils {
  // 位于: org.mybatis.spring

  // 命名:
  // SqlSession Utils = SqlSession工具

  private static final Logger LOGGER = LoggerFactory.getLogger(SqlSessionUtils.class);

  private static final String NO_EXECUTOR_TYPE_SPECIFIED = "No ExecutorType specified";
  private static final String NO_SQL_SESSION_FACTORY_SPECIFIED = "No SqlSessionFactory specified";
  private static final String NO_SQL_SESSION_SPECIFIED = "No SqlSession specified";

  /**
   * This class can't be instantiated, exposes static utility methods only.
   */
  private SqlSessionUtils() {
    // do nothing
  }

  /**
   * Creates a new MyBatis {@code SqlSession} from the {@code SqlSessionFactory} provided as a parameter and using its
   * {@code DataSource} and {@code ExecutorType}
   *
   * @param sessionFactory
   *          a MyBatis {@code SqlSessionFactory} to create new sessions
   * @return a MyBatis {@code SqlSession}
   * @throws TransientDataAccessResourceException
   *           if a transaction is active and the {@code SqlSessionFactory} is not using a
   *           {@code SpringManagedTransactionFactory}
   */
  public static SqlSession getSqlSession(SqlSessionFactory sessionFactory) {
    ExecutorType executorType = sessionFactory.getConfiguration().getDefaultExecutorType();
    return getSqlSession(sessionFactory, executorType, null);
  }

  /**
   * Gets an SqlSession from Spring Transaction Manager or creates a new one if needed. Tries to get a SqlSession out of
   * current transaction. If there is not any, it creates a new one. Then, it synchronizes the SqlSession with the
   * transaction if Spring TX is active and <code>SpringManagedTransactionFactory</code> is configured as a transaction
   * manager.
   *
   * @param sessionFactory
   *          a MyBatis {@code SqlSessionFactory} to create new sessions
   * @param executorType
   *          The executor type of the SqlSession to create
   * @param exceptionTranslator
   *          Optional. Translates SqlSession.commit() exceptions to Spring exceptions.
   * @return an SqlSession managed by Spring Transaction Manager
   * @throws TransientDataAccessResourceException
   *           if a transaction is active and the {@code SqlSessionFactory} is not using a
   *           {@code SpringManagedTransactionFactory}
   * @see SpringManagedTransactionFactory
   */
  public static SqlSession getSqlSession(SqlSessionFactory sessionFactory, ExecutorType executorType, PersistenceExceptionTranslator exceptionTranslator) {

    notNull(sessionFactory, NO_SQL_SESSION_FACTORY_SPECIFIED);
    notNull(executorType, NO_EXECUTOR_TYPE_SPECIFIED);

    // 1. note: 从事务同步器中获取一个SqlSessionHolder -> 当前前提是有@Transactional注解哦
    // 场景:
    //    @Autowired
    //    private UserMapper userMapper;
    //    @Transactional
    //    public void addUser(User  user){
    //       userMapper.addUser(user)
    //    }
    // 说明: 当执行addUser()方法时,由于有@Transactional注解 -> 触发系列事务动作 [详情见Spring中@Transactional生效的源码]
    // 其中有一点就是在拦截addUser()方法的执行,生成事务对象,并向事务同步管理器器设置中的resource中设置 SessionFactory 对应的 SqlSessionHolder
    SqlSessionHolder holder = (SqlSessionHolder) TransactionSynchronizationManager.getResource(sessionFactory);

    // 2. 尝试从holder中获取session,并处理SqlSessionHolder的相关数据
    SqlSession session = sessionHolder(executorType, holder);
    if (session != null) {
      return session;
    }

    // 3. 事务同步器没有加载过SqlSession -> 直接用给定的SqlSessionFactory去创建一个SqlSession吧 [大部分情况: DefaultSqlSessionFactory#openSession()]
    // 情况1: 用户的方法根本没有使用@Transactional注解 [50%的情况]
    // 情况2: 用户的方法根本没有使用了@Transactional注解,但是没有调用过 TransactionSynchronizationManager.bindResource(sessionFactory, holder) [50%的情况]
    // note: spring源码中是不是主动去添加执行 TransactionSynchronizationManager.bindResource(SqlSessionFactory, SqlSessionHolder)
    // 原因就一点: Spring根本就没有SqlSessionHolder这个类,更不用提注册啦 -> ❗️❗️❗️ 所以实际上在在后面的 registerSessionHolder(..) 方法中执行的
    LOGGER.debug(() -> "Creating a new SqlSession");
    session = sessionFactory.openSession(executorType);

    // 4. 注册: ❗️❗️❗️
    // 将SqlSessionHolder注册到TransactionSynchronizationManager的resource中
    // 作用:
    //    @Autowired
    //    private UserMapper userMapper;
    //    @Autowired
    //    private RoleMapper roleMapper;
    //    @Transactional
    //    public void addUser(User  user){
    //       userMapper.addUser(user);
    //       roleMapper.addRoleAndUser(user);
    //    }
    // 保证: UserMapper和roleMapper使用同一个SqlSession
    registerSessionHolder(sessionFactory, executorType, exceptionTranslator, session);

    return session;
  }

  /**
   * Register session holder if synchronization is active (i.e. a Spring TX is active).
   *
   * Note: The DataSource used by the Environment should be synchronized with the transaction either through
   * DataSourceTxMgr or another tx synchronization. Further assume that if an exception is thrown, whatever started the
   * transaction will handle closing / rolling back the Connection associated with the SqlSession.
   *
   * @param sessionFactory
   *          sqlSessionFactory used for registration.
   * @param executorType
   *          executorType used for registration.
   * @param exceptionTranslator
   *          persistenceExceptionTranslator used for registration.
   * @param session
   *          sqlSession used for registration.
   */
  private static void registerSessionHolder(SqlSessionFactory sessionFactory, ExecutorType executorType, PersistenceExceptionTranslator exceptionTranslator, SqlSession session) {
    SqlSessionHolder holder;
    // 1. 事务同步器 -> 只有在有@Transactional注解的时候一般才会开始事务同步激活哦
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      // 2. ❗️❗️❗️ 这里的Environment是Mybatis的概念,指的是当前执行的环境是哪一个,比如针对一个项目,可以有 dev qa pre prd 四个环境的数据库
      // -> 通过Mybatis的environment即可使用指定环境的数据库
      Environment environment = sessionFactory.getConfiguration().getEnvironment();

      // 3. Mybatis提供的TransactionFactory只有JDBCTransactionFactory和ManagedTransactionFactory
      if (environment.getTransactionFactory() instanceof SpringManagedTransactionFactory) {
        LOGGER.debug(() -> "Registering transaction synchronization for SqlSession [" + session + "]");

        // 3.1 创建 SqlSessionHolder(..) -> 然后绑定到事务管理同步器TransactionSynchronizationManager的resource上
        holder = new SqlSessionHolder(session, executorType, exceptionTranslator);
        TransactionSynchronizationManager.bindResource(sessionFactory, holder);
        // 3.2 添加一个同步器SqlSessionSynchronization ❗️❗️❗️
        TransactionSynchronizationManager.registerSynchronization(new SqlSessionSynchronization(holder, sessionFactory));
        // 3.3 SqlSessionHolder的数据处理: 引用计算+1 \ 设置为事务同步的
        holder.setSynchronizedWithTransaction(true);
        holder.requested();
      } else {
        if (TransactionSynchronizationManager.getResource(environment.getDataSource()) == null) {
          LOGGER.debug(() -> "SqlSession [" + session + "] was not registered for synchronization because DataSource is not transactional");
        } else {
          // 不符合规矩: 就会抛出异常哦
          throw new TransientDataAccessResourceException("SqlSessionFactory must be using a SpringManagedTransactionFactory in order to use Spring transaction synchronization");
        }
      }
    } else {
      LOGGER.debug(() -> "SqlSession [" + session + "] was not registered for synchronization because synchronization is not active");
    }

  }

  private static SqlSession sessionHolder(ExecutorType executorType, SqlSessionHolder holder) {

    // 1. holder不为空,且和当前执行的事务同步 [大部分情况 holder 都是空的哦]
    SqlSession session = null;
    if (holder != null && holder.isSynchronizedWithTransaction()) {
      // 1.1 检查executorType和Mapper接口的executorType是否相同 -> 不同就报错
      if (holder.getExecutorType() != executorType) {
        throw new TransientDataAccessResourceException(
            "Cannot change the ExecutorType when there is an existing transaction");
      }

      // 1.2 由于后续回去获取session,因此引用计数++
      holder.requested();

      // 1.3 拿到SqlSession
      LOGGER.debug(() -> "Fetched SqlSession [" + holder.getSqlSession() + "] from current transaction");
      session = holder.getSqlSession();
    }

    // 2. 返回session [99%的情况都是空的哦]
    return session;
  }

  /**
   * Checks if {@code SqlSession} passed as an argument is managed by Spring {@code TransactionSynchronizationManager}
   * If it is not, it closes it, otherwise it just updates the reference counter and lets Spring call the close callback
   * when the managed transaction ends
   *
   * @param session
   *          a target SqlSession
   * @param sessionFactory
   *          a factory of SqlSession
   */
  public static void closeSqlSession(SqlSession session, SqlSessionFactory sessionFactory) {
    notNull(session, NO_SQL_SESSION_SPECIFIED);
    notNull(sessionFactory, NO_SQL_SESSION_FACTORY_SPECIFIED);

    SqlSessionHolder holder = (SqlSessionHolder) TransactionSynchronizationManager.getResource(sessionFactory);
    if ((holder != null) && (holder.getSqlSession() == session)) {
      LOGGER.debug(() -> "Releasing transactional SqlSession [" + session + "]");
      holder.released();
    } else {
      LOGGER.debug(() -> "Closing non transactional SqlSession [" + session + "]");
      session.close();
    }
  }

  /**
   * Returns if the {@code SqlSession} passed as an argument is being managed by Spring
   *
   * @param session
   *          a MyBatis SqlSession to check
   * @param sessionFactory
   *          the SqlSessionFactory which the SqlSession was built with
   * @return true if session is transactional, otherwise false
   */
  public static boolean isSqlSessionTransactional(SqlSession session, SqlSessionFactory sessionFactory) {
    // 返回形参SqlSession是否由 Spring 管理

    notNull(session, NO_SQL_SESSION_SPECIFIED);
    notNull(sessionFactory, NO_SQL_SESSION_FACTORY_SPECIFIED);

    SqlSessionHolder holder = (SqlSessionHolder) TransactionSynchronizationManager.getResource(sessionFactory);

    return (holder != null) && (holder.getSqlSession() == session);
  }

  /**
   * Callback for cleaning up resources. It cleans TransactionSynchronizationManager and also commits and closes the
   * {@code SqlSession}. It assumes that {@code Connection} life cycle will be managed by
   * {@code DataSourceTransactionManager} or {@code JtaTransactionManager}
   */
  private static final class SqlSessionSynchronization extends TransactionSynchronizationAdapter {

    private final SqlSessionHolder holder;

    private final SqlSessionFactory sessionFactory;

    private boolean holderActive = true;

    public SqlSessionSynchronization(SqlSessionHolder holder, SqlSessionFactory sessionFactory) {
      notNull(holder, "Parameter 'holder' must be not null");
      notNull(sessionFactory, "Parameter 'sessionFactory' must be not null");

      this.holder = holder;
      this.sessionFactory = sessionFactory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrder() {
      // order right before any Connection synchronization
      return DataSourceUtils.CONNECTION_SYNCHRONIZATION_ORDER - 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void suspend() {
      if (this.holderActive) {
        LOGGER.debug(() -> "Transaction synchronization suspending SqlSession [" + this.holder.getSqlSession() + "]");
        TransactionSynchronizationManager.unbindResource(this.sessionFactory);
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resume() {
      if (this.holderActive) {
        LOGGER.debug(() -> "Transaction synchronization resuming SqlSession [" + this.holder.getSqlSession() + "]");
        TransactionSynchronizationManager.bindResource(this.sessionFactory, this.holder);
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeCommit(boolean readOnly) {
      // Connection commit or rollback will be handled by ConnectionSynchronization or
      // DataSourceTransactionManager.
      // But, do cleanup the SqlSession / Executor, including flushing BATCH statements so
      // they are actually executed.
      // SpringManagedTransaction will no-op the commit over the jdbc connection
      // TODO This updates 2nd level caches but the tx may be rolledback later on!
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
        try {
          LOGGER.debug(() -> "Transaction synchronization committing SqlSession [" + this.holder.getSqlSession() + "]");
          this.holder.getSqlSession().commit();
        } catch (PersistenceException p) {
          if (this.holder.getPersistenceExceptionTranslator() != null) {
            DataAccessException translated = this.holder.getPersistenceExceptionTranslator()
                .translateExceptionIfPossible(p);
            if (translated != null) {
              throw translated;
            }
          }
          throw p;
        }
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeCompletion() {
      // Issue #18 Close SqlSession and deregister it now
      // because afterCompletion may be called from a different thread
      if (!this.holder.isOpen()) {
        LOGGER
            .debug(() -> "Transaction synchronization deregistering SqlSession [" + this.holder.getSqlSession() + "]");
        TransactionSynchronizationManager.unbindResource(sessionFactory);
        this.holderActive = false;
        LOGGER.debug(() -> "Transaction synchronization closing SqlSession [" + this.holder.getSqlSession() + "]");
        this.holder.getSqlSession().close();
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterCompletion(int status) {
      if (this.holderActive) {
        // afterCompletion may have been called from a different thread
        // so avoid failing if there is nothing in this one
        LOGGER
            .debug(() -> "Transaction synchronization deregistering SqlSession [" + this.holder.getSqlSession() + "]");
        TransactionSynchronizationManager.unbindResourceIfPossible(sessionFactory);
        this.holderActive = false;
        LOGGER.debug(() -> "Transaction synchronization closing SqlSession [" + this.holder.getSqlSession() + "]");
        this.holder.getSqlSession().close();
      }
      this.holder.reset();
    }
  }

}
