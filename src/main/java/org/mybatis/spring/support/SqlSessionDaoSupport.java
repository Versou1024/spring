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
package org.mybatis.spring.support;

import static org.springframework.util.Assert.notNull;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.dao.support.DaoSupport;

/**
 * Convenient super class for MyBatis SqlSession data access objects. It gives you access to the template which can then
 * be used to execute SQL methods.
 * <p>
 * This class needs a SqlSessionTemplate or a SqlSessionFactory. If both are set the SqlSessionFactory will be ignored.
 * <p>
 *
 * @author Putthiphong Boonphong
 * @author Eduardo Macarron
 *
 * @see #setSqlSessionFactory
 * @see #setSqlSessionTemplate
 * @see SqlSessionTemplate
 */
public abstract class SqlSessionDaoSupport extends DaoSupport {
  // 命名:
  // SqlSessionDaoSupport = Sql Session DaoSupport
  // 要求: 必须注入SqlSessionTemplate,否则在DaoSupport的初始化方法afterPropertiesSet()中调用checkDaoConfig()会报错!!!
  // 此类需要 SqlSessionTemplate 或 SqlSessionFactory,如果两者都设置了 SqlSessionFactory 将被忽略。

  private SqlSessionTemplate sqlSessionTemplate;

  /**
   * Set MyBatis SqlSessionFactory to be used by this DAO. Will automatically create SqlSessionTemplate for the given
   * SqlSessionFactory.
   *
   * @param sqlSessionFactory
   *          a factory of SqlSession
   */
  public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
    // sqlSessionFactory: 位于 org.apache.ibatis.session

    // 设置当前 DAO 使用的 sqlSessionTemplate: new SqlSessionTemplate(sqlSessionFactory)
    if (this.sqlSessionTemplate == null || sqlSessionFactory != this.sqlSessionTemplate.getSqlSessionFactory()) {
      this.sqlSessionTemplate = createSqlSessionTemplate(sqlSessionFactory);
    }
  }

  /**
   * Create a SqlSessionTemplate for the given SqlSessionFactory. Only invoked if populating the DAO with a
   * SqlSessionFactory reference!
   * <p>
   * Can be overridden in subclasses to provide a SqlSessionTemplate instance with different configuration, or a custom
   * SqlSessionTemplate subclass.
   *
   * @param sqlSessionFactory
   *          the MyBatis SqlSessionFactory to create a SqlSessionTemplate for
   * @return the new SqlSessionTemplate instance
   * @see #setSqlSessionFactory
   */
  @SuppressWarnings("WeakerAccess")
  protected SqlSessionTemplate createSqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
    return new SqlSessionTemplate(sqlSessionFactory);
  }

  /**
   * Return the MyBatis SqlSessionFactory used by this DAO.
   *
   * @return a factory of SqlSession
   */
  public final SqlSessionFactory getSqlSessionFactory() {
    return (this.sqlSessionTemplate != null ? this.sqlSessionTemplate.getSqlSessionFactory() : null);
  }

  /**
   * Set the SqlSessionTemplate for this DAO explicitly, as an alternative to specifying a SqlSessionFactory.
   *
   * @param sqlSessionTemplate
   *          a template of SqlSession
   * @see #setSqlSessionFactory
   */
  public void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
    // 直接设置sqlSessionTemplate,将导致setSqlSessionFactory()执行失效

    this.sqlSessionTemplate = sqlSessionTemplate;
  }

  /**
   * Users should use this method to get a SqlSession to call its statement methods This is SqlSession is managed by
   * spring. Users should not commit/rollback/close it because it will be automatically done.
   *
   * @return Spring managed thread safe SqlSession
   */
  public SqlSession getSqlSession() {
    return this.sqlSessionTemplate;
  }

  /**
   * Return the SqlSessionTemplate for this DAO, pre-initialized with the SessionFactory or set explicitly.
   * <p>
   * <b>Note: The returned SqlSessionTemplate is a shared instance.</b> You may introspect its configuration, but not
   * modify the configuration (other than from within an {@link #initDao} implementation). Consider creating a custom
   * SqlSessionTemplate instance via {@code new SqlSessionTemplate(getSqlSessionFactory())}, in which case you're
   * allowed to customize the settings on the resulting instance.
   *
   * @return a template of SqlSession
   */
  public SqlSessionTemplate getSqlSessionTemplate() {
    return this.sqlSessionTemplate;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void checkDaoConfig() {
    // ❗️❗️❗️
    // 要求: 必须有注入SqlSessionTemplate[本质就是一个SqlSession] 或者 必须注入 SqlSessionFactory来创建SqlSession
    notNull(this.sqlSessionTemplate, "Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required");
  }

}
