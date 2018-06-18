<%--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
--%>
<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@page import="org.apache.syncope.common.lib.to.OIDCLoginResponseTO"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<c:set var="responseTO" value="${requestScope['responseTO']}"/>
<html>
  <head>
    <title>Apache Syncope ${syncope.version} - OIDC CLIENT - Successful Login</title>
  </head>
  <body>
    <h1>Welcome ${responseTO.userName}</h1>

    <p>You have been successfully authenticated by the requested OIDC Provider</p>

  </body>
</html>