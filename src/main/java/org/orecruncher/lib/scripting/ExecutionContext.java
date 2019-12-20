/*
 * Dynamic Surroundings: Sound Control
 * Copyright (C) 2019  OreCruncher
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>
 */

package org.orecruncher.lib.scripting;

import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.orecruncher.lib.Lib;
import org.orecruncher.lib.collections.ObjectArray;
import org.orecruncher.lib.logging.IModLog;

import javax.annotation.Nonnull;
import javax.script.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ExecutionContext {

    private static final IModLog LOGGER = Lib.LOGGER;

    /**
     * Special ClassFilter that locks everything down to prevent execution of code outside of what should be
     * available to configurations.  Goal is to prevent bad scripts from damaging the Minecraft runtime environment
     * either by intent or accident.
     */
    private static final ClassFilter FILTER = s -> false;

    private static final String FUNCTION_SHELL = "%s;";

    @Nonnull
    private final String contextName;
    @Nonnull
    private final ScriptEngine engine;
    @Nonnull
    private final ObjectArray<VariableSet<?>> variables = new ObjectArray<>(8);
    @Nonnull
    private final Map<String, CompiledScript> compiled = new HashMap<>();
    @Nonnull
    private final CompiledScript error;

    public ExecutionContext(@Nonnull final String contextName) {
        this.contextName = contextName;
        this.engine = new NashornScriptEngineFactory().getScriptEngine(FILTER);
        this.error = makeFunction("'<ERROR>'");
        this.engine.put("lib", new LibraryFunctions());
    }

    public void add(@Nonnull final VariableSet<?> varSet) {
        if (this.engine.get(varSet.getName()) != null)
            throw new IllegalStateException(String.format("Variable set '%s' already defined!", varSet.getName()));

        this.variables.add(varSet);
        this.engine.put(varSet.getName(), varSet.getInterface());
    }

    public String getName() {
        return this.contextName;
    }

    public void update() {
        this.variables.forEach(VariableSet::update);
    }

    public boolean check(@Nonnull final String script) {
        final Optional<Object> result = eval(script);
        if (result.isPresent())
            return "true".equalsIgnoreCase(result.toString());
        return false;
    }

    @Nonnull
    public Optional<Object> eval(@Nonnull final String script) {
        CompiledScript func = compiled.get(script);
        if (func == null) {
            func = makeFunction(script);
            compiled.put(script, func);
        }

        try {
            return Optional.of(func.eval());
        } catch (@Nonnull final Throwable t) {
            LOGGER.error(t, "Error execution script: %s", script);
            compiled.put(script, this.error);
        }

        return Optional.of("ERROR?");
    }

    @Nonnull
    private CompiledScript makeFunction(@Nonnull final String script) {
        final String source = String.format(FUNCTION_SHELL, script);
        try {
            return ((Compilable) this.engine).compile(source);
        } catch (@Nonnull final Throwable t) {
            LOGGER.error(t, "Error compiling script: %s", source);
        }
        return this.error;
    }

}
