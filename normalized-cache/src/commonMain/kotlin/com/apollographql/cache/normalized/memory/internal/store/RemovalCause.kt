/*
 * Taken from the Mobile Native Foundation Store project.
 * https://github.com/MobileNativeFoundation/Store/commit/e25e3c130187d9294ad5b998136b0498bd91d88f
 *
 * Copyright (c) 2017 The New York Times Company
 *
 * Copyright (C) 2009 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.apollographql.cache.normalized.memory.internal.store

import com.apollographql.cache.normalized.memory.internal.store.RemovalCause.EXPLICIT
import com.apollographql.cache.normalized.memory.internal.store.RemovalCause.REPLACED


/**
 * The reason why a cached entry was removed.
 * @param wasEvicted True if entry removal was automatic due to eviction. That is, the cause of removal is neither [EXPLICIT] or [REPLACED].
 * @author Charles Fry
 * @since 10.0
 */
internal enum class RemovalCause(val wasEvicted: Boolean) {
  EXPLICIT(false),
  REPLACED(false),
  COLLECTED(true),
  EXPIRED(true),
  SIZE(true);
}
