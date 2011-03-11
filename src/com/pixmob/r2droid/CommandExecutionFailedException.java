/*
 * Copyright (C) 2011 Alexandre Roman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pixmob.r2droid;

/**
 * Error when a command failed to execute.
 * @author Pixmob
 */
public class CommandExecutionFailedException extends Exception {
    private static final long serialVersionUID = 1L;
    
    public CommandExecutionFailedException(final String message) {
        this(message, null);
    }
    
    public CommandExecutionFailedException(final Throwable cause) {
        this("Command execution failed", cause);
    }
    
    public CommandExecutionFailedException(final String message,
            final Throwable cause) {
        super(message, cause);
    }
}
