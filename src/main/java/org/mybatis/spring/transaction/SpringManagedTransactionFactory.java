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
package org.mybatis.spring.transaction;

import java.sql.Connection;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;

/**
 * Creates a {@code SpringManagedTransaction}.
 *
 * @author Hunter Presnall
 */
public class SpringManagedTransactionFactory implements TransactionFactory {
  // 位于: org.mybatis.spring.transaction

  // 命名:
  // Spring Managed TransactionFactory =  受到Spring管理的TransactionFactory事务工厂

  // 作用:
  // 生成: SpringManagedTransaction Spring管理的事务对象[耦合Mybatis的Transaction,耦合Spring的事务同步管理器TransactionSynchronizationManager]

  // TransactionFactory实现类:
  // Mybatis只有JdbcTransactionFactory和ManagedTransactionFactory两种实现哦
  // Mybatis的Transaction

  // 了解:
  // TransactionFactory的方法:
  //    Transaction newTransaction(Connection conn) -> 从jdbc连接创建transaction
  //    Transaction newTransaction(DataSource dataSource, TransactionIsolationLevel level, boolean autoCommit) -> 从dataSource创建Transaction
  // Transaction的方法:
  //    getConnection():Connection
  //    commit():void
  //    rollback():void
  //    close():void
  //    getTimeout():Integer
  //    其实现类JdbcTransaction -> connection\dataSource\事务隔离级别\是否自动提交


  // 只支持:使用DataSource来为其创建SpringManagedTransaction

  /**
   * {@inheritDoc}
   */
  @Override
  public Transaction newTransaction(DataSource dataSource, TransactionIsolationLevel level, boolean autoCommit) {
    return new SpringManagedTransaction(dataSource);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Transaction newTransaction(Connection conn) {
    throw new UnsupportedOperationException("New Spring transactions require a DataSource");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setProperties(Properties props) {
    // not needed in this version
  }

}
