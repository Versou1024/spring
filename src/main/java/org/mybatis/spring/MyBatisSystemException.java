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

import org.springframework.dao.UncategorizedDataAccessException;

/**
 * MyBatis specific subclass of {@code UncategorizedDataAccessException}, for MyBatis system errors that do not match
 * any concrete {@code org.springframework.dao} exceptions.
 *
 * In MyBatis 3 {@code org.apache.ibatis.exceptions.PersistenceException} is a {@code RuntimeException}, but using this
 * wrapper class to bring everything under a single hierarchy will be easier for client code to handle.
 *
 * @author Hunter Presnall
 */
@SuppressWarnings("squid:MaximumInheritanceDepth") // It is the intended design
public class MyBatisSystemException extends UncategorizedDataAccessException {
  // 命名:
  // MyBatis System Exception = Mybatis系统异常

  // 继承体系:
  // UncategorizedDataAccessException 来源于 org.springframework.dao
  // 且属于 DataAccessException 异常体系的一员

  // 作用:
  // 在 MyBatisExceptionTranslator 中遇到无法翻译的Exception时,统统转换为 MyBatisSystemException

  private static final long serialVersionUID = -5284728621670758939L;

  public MyBatisSystemException(Throwable cause) {
    super(null, cause);
  }

}
