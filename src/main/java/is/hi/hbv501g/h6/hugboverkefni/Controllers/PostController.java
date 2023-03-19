package is.hi.hbv501g.h6.hugboverkefni.Controllers;

import is.hi.hbv501g.h6.hugboverkefni.Persistence.Entities.*;
import is.hi.hbv501g.h6.hugboverkefni.Services.CloudinaryService;
import is.hi.hbv501g.h6.hugboverkefni.Services.Implementations.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
public class PostController {
    private final PostServiceImplementation postService;
    private final UserServiceImplementation userService;
    private final ReplyServiceImplementation replyService;
    private final SubServiceImplementation subService;
    private final VoteServiceImplementation voteService;
    private final CloudinaryService cloudinaryService;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    @Autowired
    public PostController(PostServiceImplementation postService,
                          UserServiceImplementation userService,
                          ReplyServiceImplementation replyService,
                          SubServiceImplementation subService,
                          VoteServiceImplementation voteService,
                          CloudinaryService cloudinaryService) {
        this.postService = postService;
        this.userService = userService;
        this.replyService = replyService;
        this.subService = subService;
        this.voteService = voteService;
        this.cloudinaryService = cloudinaryService;
        this.markdownParser = Parser.builder().build();
        this.htmlRenderer = HtmlRenderer.builder().build();
    }

    @RequestMapping(value = "/p/{slug}/{id}", method = RequestMethod.GET)
    public ResponseEntity<Post> postPage(@PathVariable("slug") String slug, @PathVariable("id") long id, Model model) {
        Optional<Post> post = postService.getPostById(id);
        if(!post.isPresent()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        return new ResponseEntity<Post>(post.get(), HttpStatus.OK);
    }

    @RequestMapping(value = "/p/{slug}/newPost", method = RequestMethod.POST)
    public ResponseEntity<Post> newPostPOST(@PathVariable String slug, String title, String text, @RequestParam("image") MultipartFile image, @RequestParam("audio") MultipartFile audio, @RequestParam("recording") String recording, Model model, HttpSession session) {
        Sub sub = subService.getSubBySlug(slug);
        String renderedText = htmlRenderer.render(markdownParser.parse(text));
        Post newPost = createPost(title, sub, renderedText, image, audio, recording, session);
        postService.addNewPost(newPost);
        return new ResponseEntity<Post>(newPost, HttpStatus.OK);
    }


    @RequestMapping(value = "/p/{slug}/{id}", method = RequestMethod.POST)
    public ResponseEntity replyPost(@PathVariable String slug, @PathVariable("id") long id, String text, @RequestParam("image") MultipartFile image, @RequestParam("audio") MultipartFile audio, @RequestParam("recording") String recording, Model model, RedirectAttributes redirectAttributes, HttpSession session) {
        Optional<Post> post = postService.getPostById(id);
        if (!post.isPresent()) return new ResponseEntity(HttpStatus.NOT_FOUND);

        if(text.isEmpty() && image.isEmpty() && audio.isEmpty() && recording.equals("recording")) {
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }

        Sub sub = subService.getSubBySlug(slug);
        String renderedText = htmlRenderer.render(markdownParser.parse(text));
        Reply reply = createReply(renderedText, sub, image, audio, recording, session);
        replyService.addNewReply(reply);
        post.get().addReply(reply);
        postService.addNewPost(post.get());

        return new ResponseEntity(HttpStatus.CREATED);
    }


    @RequestMapping(value = "/p/{slug}/{postId}/{id}", method = RequestMethod.POST)
    public ResponseEntity replyReply(@PathVariable String slug, @PathVariable("postId") long postId, @PathVariable("id") long id, String text, @RequestParam("image") MultipartFile image, @RequestParam("audio") MultipartFile audio, @RequestParam("recording") String recording, Model model, RedirectAttributes redirectAttributes, HttpSession session) {
        Optional<Reply> prevReply = replyService.getReplyById(id);
        if (!prevReply.isPresent()) return new ResponseEntity(HttpStatus.NOT_FOUND);
        if(text.isEmpty() && image.isEmpty() && audio.isEmpty() && recording.equals("recording")) {
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }

        Sub sub = subService.getSubBySlug(slug);
        String renderedText = htmlRenderer.render(markdownParser.parse(text));
        Reply reply = createReply(renderedText, sub, image, audio, recording, session);
        replyService.addNewReply(reply);
        prevReply.get().addReply(reply);
        replyService.addNewReply(prevReply.get());

        return new ResponseEntity(HttpStatus.CREATED);
    }

    @RequestMapping(value = "/r/{id}/vote", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity getReplyVote(@PathVariable("id") long id, Model model) {
        Optional<Reply> reply = replyService.getReplyById(id);
        if(!reply.isPresent()) return new ResponseEntity(HttpStatus.NOT_FOUND);

        return new ResponseEntity(reply.get(), HttpStatus.OK);
    }

    @RequestMapping(value = "/r/{id}/upvote", method = RequestMethod.POST)
    public String upvoteReply(@PathVariable("id") long id, HttpSession session) {
        return changeReplyVote(id, true, session);
    }

    @RequestMapping(value = "/r/{id}/downvote", method = RequestMethod.POST)
    public String downvoteReply(@PathVariable("id") long id, HttpSession session) {
        return changeReplyVote(id, false, session);
    }

    @RequestMapping(value = "/r/{id}/upvote", method = RequestMethod.GET)
    public String getUpvoteReply(@PathVariable("id") long id, HttpSession session) {

        return changeReplyVote(id, true, session);
    }

    @RequestMapping(value = "/r/{id}/downvote", method = RequestMethod.GET)
    public String getDownvoteReply(@PathVariable("id") long id, HttpSession session) {

        return changeReplyVote(id, false, session);
    }


    public String changeReplyVote(long id, Boolean upvote, HttpSession session) {
        Reply reply = replyService.getReplyById(id).get();
        User user = (User) session.getAttribute("user");

        Optional<Voter> voter = reply.findVoter(user);

        if (voter.isEmpty()) {
            Voter newVoter = new Voter(user, upvote);
            reply.addVote(newVoter);
            voteService.addVoter(newVoter);
        } else if (voter.get().isVote() != upvote) {
            voter.get().setVote(upvote);
        } else {
            reply.removeVote(voter.get());
            voteService.removeVoter(voter.get());
        }

        replyService.addNewReply(reply);

        return "frontPage.html";
    }

    @RequestMapping(value = "/p/{id}/vote", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity getPostVote(@PathVariable("id") long id, Model model) {
        Optional<Post> post = postService.getPostById(id);
        if(!post.isPresent()) return new ResponseEntity(HttpStatus.NOT_FOUND);
        return new ResponseEntity(post.get(), HttpStatus.OK);
    }

    @RequestMapping(value = "/p/{id}/upvote", method = RequestMethod.POST)
    public String upvotePost(@PathVariable("id") long id, HttpSession session) {

        return changePostVote(id, true, session);
    }

    @RequestMapping(value = "/p/{id}/upvote", method = RequestMethod.GET)
    public String getUpvote(@PathVariable("id") long id, HttpSession session) {

        return changePostVote(id, true, session);
    }

    @RequestMapping(value = "/p/{id}/downvote", method = RequestMethod.POST)
    public String downvotePost(@PathVariable("id") long id, HttpSession session) {
        return changePostVote(id, false, session);

    }

    @RequestMapping(value = "/p/{id}/downvote", method = RequestMethod.GET)
    public String getDownvote(@PathVariable("id") long id, HttpSession session) {
        return changePostVote(id, false, session);

    }


    public String changePostVote(long id, Boolean upvote, HttpSession session) {
        Post post = postService.getPostById(id).get();
        User user = (User) session.getAttribute("user");

        Optional<Voter> voter = post.findVoter(user);

        if (voter.isEmpty()) {
            Voter newVoter = new Voter(user, upvote);
            post.addVote(newVoter);
            voteService.addVoter(newVoter);
        } else if (voter.get().isVote() != upvote) {
            voter.get().setVote(upvote);
        } else {
            post.removeVote(voter.get());
            voteService.removeVoter(voter.get());
        }

        postService.addNewPost(post);

        return "frontPage.html";
    }

    private Post createPost(String title, Sub sub, String text, MultipartFile image, MultipartFile audio, String recording, HttpSession session) {
        Content content = createContent(text, image, audio, recording);

        User user = (User) session.getAttribute("user");
        if (user != null) return new Post(title, sub, content, user, new ArrayList<Voter>(), new ArrayList<Reply>());

        return new Post(title, sub, content, userService.getAnon(), new ArrayList<Voter>(), new ArrayList<Reply>());
    }


    private Reply createReply(String text, Sub sub, MultipartFile image, MultipartFile audio, String recording, HttpSession session) {
        Content content = createContent(text, image, audio, recording);

        User user = (User) session.getAttribute("user");
        if (user != null) return new Reply(content, user, new ArrayList<Voter>(), new ArrayList<Reply>(), sub);

        return new Reply(content, userService.getAnon(), new ArrayList<Voter>(), new ArrayList<Reply>(), sub);
    }

    private Content createContent(String text, MultipartFile image, MultipartFile audio, String recording) {
        String imgUrl = "";
        String audioUrl = "";
        String recordingUrl = "";
        if (!image.isEmpty()) imgUrl = cloudinaryService.securify(cloudinaryService.uploadImage(image));
        if (!audio.isEmpty()) audioUrl = cloudinaryService.securify(cloudinaryService.uploadAudio(audio));
        if (recording.length() != 9) recordingUrl = cloudinaryService.securify(cloudinaryService.uploadRecording(recording));
        Content c = new Content(text, imgUrl, audioUrl, recordingUrl);
        return c;
    }

    private User getUser() {
        User user = userService.getUsers().get(0);
        return user;
    }

    private Sub getSub() {
        Sub sub = subService.getSubs().get(0);
        return sub;
    }
}
