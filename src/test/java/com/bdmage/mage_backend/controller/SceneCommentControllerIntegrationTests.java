package com.bdmage.mage_backend.controller;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.SceneRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.service.PasswordHashingService;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SceneCommentControllerIntegrationTests extends PostgresIntegrationTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SceneRepository sceneRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordHashingService passwordHashingService;

	@Test
	void createReplyAndListCommentsReturnsNestedStructure() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		User author = saveUser("comment-author-" + uniqueSuffix, "Comment Author");
		Scene scene = saveScene(author.getId(), "Commented Scene");
		String accessToken = accessToken(author.getEmail(), "password-" + author.getEmail());

		MvcResult parentResult = this.mockMvc.perform(post("/api/scenes/" + scene.getId() + "/comments")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"text":"First pass is hypnotic."}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sceneId").value(scene.getId()))
				.andExpect(jsonPath("$.authorUserId").value(author.getId()))
				.andExpect(jsonPath("$.authorDisplayName").value("Comment Author"))
				.andExpect(jsonPath("$.text").value("First pass is hypnotic."))
				.andExpect(jsonPath("$.replyCount").value(0L))
				.andExpect(jsonPath("$.upvotes").value(0L))
				.andExpect(jsonPath("$.downvotes").value(0L))
				.andExpect(jsonPath("$.currentUserVote").doesNotExist())
				.andReturn();

		Long parentCommentId = commentId(parentResult);
		MvcResult replyResult = this.mockMvc.perform(post("/api/presets/" + scene.getId() + "/comments")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"text":"The second drop lands even better.","parentCommentId":%d}
						""".formatted(parentCommentId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.parentCommentId").value(parentCommentId))
				.andExpect(jsonPath("$.text").value("The second drop lands even better."))
				.andReturn();

		Long replyCommentId = commentId(replyResult);

		this.mockMvc.perform(get("/api/scenes/" + scene.getId() + "/comments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].commentId").value(parentCommentId))
				.andExpect(jsonPath("$[0].sceneId").value(scene.getId()))
				.andExpect(jsonPath("$[0].parentCommentId").doesNotExist())
				.andExpect(jsonPath("$[0].authorDisplayName").value("Comment Author"))
				.andExpect(jsonPath("$[0].text").value("First pass is hypnotic."))
				.andExpect(jsonPath("$[0].replyCount").value(1L))
				.andExpect(jsonPath("$[0].upvotes").value(0L))
				.andExpect(jsonPath("$[0].downvotes").value(0L))
				.andExpect(jsonPath("$[0].currentUserVote").doesNotExist())
				.andExpect(jsonPath("$[0].replies.length()").value(1))
				.andExpect(jsonPath("$[0].replies[0].commentId").value(replyCommentId))
				.andExpect(jsonPath("$[0].replies[0].parentCommentId").value(parentCommentId))
				.andExpect(jsonPath("$[0].replies[0].text").value("The second drop lands even better."))
				.andExpect(jsonPath("$[0].replies[0].replyCount").value(0L));
	}

	@Test
	void commentVoteCanSwitchAndClearForAuthenticatedUser() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		User author = saveUser("vote-author-" + uniqueSuffix, "Vote Author");
		User voter = saveUser("vote-user-" + uniqueSuffix, "Vote User");
		Scene scene = saveScene(author.getId(), "Voted Scene");
		String authorToken = accessToken(author.getEmail(), "password-" + author.getEmail());
		String voterToken = accessToken(voter.getEmail(), "password-" + voter.getEmail());
		Long commentId = createComment(scene.getId(), authorToken, "Vote on this texture.");

		this.mockMvc.perform(put("/api/scenes/" + scene.getId() + "/comments/" + commentId + "/vote")
				.header("Authorization", "Bearer " + voterToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"vote":"up"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.upvotes").value(1L))
				.andExpect(jsonPath("$.downvotes").value(0L))
				.andExpect(jsonPath("$.currentUserVote").value("up"));

		this.mockMvc.perform(put("/api/scenes/" + scene.getId() + "/comments/" + commentId + "/vote")
				.header("Authorization", "Bearer " + voterToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"vote":"down"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.upvotes").value(0L))
				.andExpect(jsonPath("$.downvotes").value(1L))
				.andExpect(jsonPath("$.currentUserVote").value("down"));

		this.mockMvc.perform(get("/api/scenes/" + scene.getId() + "/comments")
				.header("Authorization", "Bearer " + voterToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].upvotes").value(0L))
				.andExpect(jsonPath("$[0].downvotes").value(1L))
				.andExpect(jsonPath("$[0].currentUserVote").value("down"));

		this.mockMvc.perform(delete("/api/scenes/" + scene.getId() + "/comments/" + commentId + "/vote")
				.header("Authorization", "Bearer " + voterToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.upvotes").value(0L))
				.andExpect(jsonPath("$.downvotes").value(0L))
				.andExpect(jsonPath("$.currentUserVote").doesNotExist());
	}

	@Test
	void createReplyRejectsInvalidParentReferences() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		User author = saveUser("invalid-parent-author-" + uniqueSuffix, "Invalid Parent Author");
		Scene firstScene = saveScene(author.getId(), "First Commented Scene");
		Scene secondScene = saveScene(author.getId(), "Second Commented Scene");
		String accessToken = accessToken(author.getEmail(), "password-" + author.getEmail());
		Long parentCommentId = createComment(firstScene.getId(), accessToken, "Parent comment.");
		Long replyCommentId = createReply(firstScene.getId(), accessToken, parentCommentId, "Existing reply.");

		this.mockMvc.perform(post("/api/scenes/" + secondScene.getId() + "/comments")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"text":"Cross-scene reply.","parentCommentId":%d}
						""".formatted(parentCommentId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_COMMENT_PARENT"))
				.andExpect(jsonPath("$.message").value("Parent comment must be a top-level comment on this scene."));

		this.mockMvc.perform(post("/api/scenes/" + firstScene.getId() + "/comments")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"text":"Nested reply.","parentCommentId":%d}
						""".formatted(replyCommentId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_COMMENT_PARENT"))
				.andExpect(jsonPath("$.message").value("Parent comment must be a top-level comment on this scene."));
	}

	@Test
	void commentVoteRequiresAuthentication() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		User author = saveUser("unauth-comment-author-" + uniqueSuffix, "Unauth Comment Author");
		Scene scene = saveScene(author.getId(), "Unauthenticated Vote Scene");
		String accessToken = accessToken(author.getEmail(), "password-" + author.getEmail());
		Long commentId = createComment(scene.getId(), accessToken, "Anonymous votes should fail.");

		this.mockMvc.perform(put("/api/scenes/" + scene.getId() + "/comments/" + commentId + "/vote")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"vote":"up"}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	private Long createComment(Long sceneId, String accessToken, String text) throws Exception {
		MvcResult result = this.mockMvc.perform(post("/api/scenes/" + sceneId + "/comments")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\":\"" + text + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();

		return commentId(result);
	}

	private Long createReply(Long sceneId, String accessToken, Long parentCommentId, String text) throws Exception {
		MvcResult result = this.mockMvc.perform(post("/api/scenes/" + sceneId + "/comments")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"text":"%s","parentCommentId":%d}
						""".formatted(text, parentCommentId)))
				.andExpect(status().isCreated())
				.andReturn();

		return commentId(result);
	}

	private User saveUser(String emailPrefix, String displayName) {
		String email = emailPrefix + "@example.com";
		return this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash("password-" + email),
				displayName));
	}

	private Scene saveScene(Long ownerUserId, String name) throws Exception {
		return this.sceneRepository.saveAndFlush(new Scene(
				ownerUserId,
				name,
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));
	}

	private String accessToken(String email, String password) throws Exception {
		MvcResult result = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		Matcher matcher = Pattern.compile("\"accessToken\":\"([^\"]+)\"")
				.matcher(result.getResponse().getContentAsString());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private static Long commentId(MvcResult result) throws Exception {
		Matcher matcher = Pattern.compile("\"commentId\":(\\d+)")
				.matcher(result.getResponse().getContentAsString());
		assertThat(matcher.find()).isTrue();
		return Long.valueOf(matcher.group(1));
	}
}
