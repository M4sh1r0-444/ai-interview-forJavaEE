package com.example.interview.ai.parser;

import com.example.interview.ai.model.AiNextQuestionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 计划 §8.1 AiResponseParserTest：正常 JSON / ```json 围栏包裹 / 半截 JSON。
 * 前两者解析成功，第三个抛 AiFallbackException（调用方走模板降级路径）。
 */
class AiResponseParserTest {

    private final AiResponseParser parser = new AiResponseParser();

    @Test
    void plainJsonParses() {
        AiNextQuestionResult r = parser.parse(
                "{\"action\":\"FOLLOW_UP\",\"content\":\"能举个例子吗？\",\"difficulty\":4,"
                        + "\"targetSkills\":[\"spring\"],\"reason\":\"回答缺少例子\"}",
                AiNextQuestionResult.class);
        assertThat(r.getAction()).isEqualTo("FOLLOW_UP");
        assertThat(r.getContent()).contains("例子");
        assertThat(r.getDifficulty()).isEqualTo(4);
        assertThat(r.getTargetSkills()).containsExactly("spring");
    }

    @Test
    void fencedJsonParses() {
        AiNextQuestionResult r = parser.parse("""
                好的，以下是追问：
                ```json
                {"action":"FOLLOW_UP","content":"为什么树化阈值是 8？","difficulty":4}
                ```
                """, AiNextQuestionResult.class);
        assertThat(r.getAction()).isEqualTo("FOLLOW_UP");
        assertThat(r.getContent()).contains("树化阈值");
    }

    @Test
    void truncatedJsonFallsBack() {
        assertThatThrownBy(() -> parser.parse(
                "{\"action\":\"FOLLOW_UP\",\"content\":\"能结合项目说明",
                AiNextQuestionResult.class))
                .isInstanceOf(AiFallbackException.class);
    }

    @Test
    void leadingAndTrailingTextAroundJsonIsStripped() {
        AiNextQuestionResult r = parser.parse(
                "我的判断如下：{\"action\":\"NEXT_QUESTION\",\"difficulty\":3} 以上就是我的建议。",
                AiNextQuestionResult.class);
        assertThat(r.getAction()).isEqualTo("NEXT_QUESTION");
    }

    @Test
    void noJsonAtAllFallsBack() {
        assertThatThrownBy(() -> parser.parse("抱歉，我无法回答这个问题。", AiNextQuestionResult.class))
                .isInstanceOf(AiFallbackException.class);
        assertThatThrownBy(() -> parser.parse("", AiNextQuestionResult.class))
                .isInstanceOf(AiFallbackException.class);
    }
}
