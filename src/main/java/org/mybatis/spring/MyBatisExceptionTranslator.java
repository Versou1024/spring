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

import java.sql.SQLException;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.apache.ibatis.exceptions.PersistenceException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import org.springframework.transaction.TransactionException;

/**
 * Default exception translator.
 *
 * Translates MyBatis SqlSession returned exception into a Spring {@code DataAccessException} using Spring's
 * {@code SQLExceptionTranslator} Can load {@code SQLExceptionTranslator} eagerly or when the first exception is
 * translated.
 *
 * @author Eduardo Macarron
 */
public class MyBatisExceptionTranslator implements PersistenceExceptionTranslator {
  // 命名:
  // MyBatis Exception Translator = Mybatis框架的异常翻译器

  // 作用:
  // 将Mybatis的异常翻译为Spring的异常 -> [默认被使用哦]

  // SQLExceptionTranslator 翻译器
  private final Supplier<SQLExceptionTranslator> exceptionTranslatorSupplier;

  // SQLExceptionTranslator 翻译器
  // 默认: SQLErrorCodeSQLExceptionTranslator
  private SQLExceptionTranslator exceptionTranslator;

  /**
   * Creates a new {@code PersistenceExceptionTranslator} instance with {@code SQLErrorCodeSQLExceptionTranslator}.
   *
   * @param dataSource
   *          DataSource to use to find metadata and establish which error codes are usable.
   * @param exceptionTranslatorLazyInit
   *          if true, the translator instantiates internal stuff only the first time will have the need to translate
   *          exceptions.
   */
  public MyBatisExceptionTranslator(DataSource dataSource, boolean exceptionTranslatorLazyInit) {
    this(() -> new SQLErrorCodeSQLExceptionTranslator(dataSource), exceptionTranslatorLazyInit);
  }

  /**
   * Creates a new {@code PersistenceExceptionTranslator} instance with specified {@code SQLExceptionTranslator}.
   *
   * @param exceptionTranslatorSupplier
   *          Supplier for creating a {@code SQLExceptionTranslator} instance
   * @param exceptionTranslatorLazyInit
   *          if true, the translator instantiates internal stuff only the first time will have the need to translate
   *          exceptions.
   * @since 2.0.3
   */
  public MyBatisExceptionTranslator(Supplier<SQLExceptionTranslator> exceptionTranslatorSupplier,
      boolean exceptionTranslatorLazyInit) {
    // 允许懒加载 -> 后续第一次调用translateExceptionIfPossible(..)再去调用initExceptionTranslator()初始化获取SQLErrorCodeSQLExceptionTranslator
    this.exceptionTranslatorSupplier = exceptionTranslatorSupplier;
    if (!exceptionTranslatorLazyInit) {
      this.initExceptionTranslator();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DataAccessException translateExceptionIfPossible(RuntimeException e) {
    // 重写: PersistenceExceptionTranslator#translateExceptionIfPossible(..)

    // 1. 只针对Mybatis的持久化异常PersistenceException进行转换
    if (e instanceof PersistenceException) {
      // 1.1 批处理异常进入另一个 PersistenceException 递归有无限循环的风险，所以最好再做一个 if
      if (e.getCause() instanceof PersistenceException) {
        e = (PersistenceException) e.getCause();
      }
      // 1.2 cause属于SQLException
      if (e.getCause() instanceof SQLException) {
        this.initExceptionTranslator();
        String task = e.getMessage() + "\n";
        SQLException se = (SQLException) e.getCause();
        // 1.2.1 使用 SQLErrorCodeSQLExceptionTranslator 翻译异常信息
        DataAccessException dae = this.exceptionTranslator.translate(task, null, se);
        return dae != null ? dae : new UncategorizedSQLException(task, null, se);
      }
      // 1.3 TransactionException -> 属于已经被事务的异常
      else if (e.getCause() instanceof TransactionException) {
        throw (TransactionException) e.getCause();
      }
      // 1.4 对于非上述的异常,统统转换为Mybatis的系统异常 -> [从继承关系上而言,MyBatisSystemException间接实现了Spring的DataAccessException]
      return new MyBatisSystemException(e);
    }
    return null;
  }

  /**
   * Initializes the internal translator reference.
   */
  private synchronized void initExceptionTranslator() {
    if (this.exceptionTranslator == null) {
      this.exceptionTranslator = exceptionTranslatorSupplier.get();
    }
  }

}
