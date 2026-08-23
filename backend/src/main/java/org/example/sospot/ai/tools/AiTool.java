package org.example.sospot.ai.tools;

import java.util.Map;

public interface AiTool {

    String name();

    Map<String, Object> schema();

    Object execute(Map<String, Object> args);
}
