package org.nexial.core.plugins.web;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * POJO class that represents the email information with the following:-
 * <ul>
 *     <li>Subject({@link EmailDetails#subject}) of the Email</li>
 *     <li>Sender({@link EmailDetails#from}) of the Email</li>
 *     <li>Receiver({@link EmailDetails#to}) of the Email</li>
 *     <li>Content({@link EmailDetails#content}) of the Email</li>
 *     <li>HTML content({@link EmailDetails#html}) of the Email</li>
 *     <li>links({@link EmailDetails#links}) in the Email</li>
 *     <li>Time({@link EmailDetails#time}) at which the email is received.</li>
 *     <li>Id ({@link EmailDetails#id}) of the email. This is generally the Id of the tr element.</li>
 *     <li>Screenshot (({@link EmailDetails#screenShot}) of the email.</li>
 * </ul>
 */
public class EmailDetails {
    private String id;
    private String subject;
    private String from;
    private LocalDateTime time;
    private String to;
    private String content;
    private Set<String> links;
    private String html;
    private String screenShot;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Set<String> getLinks() {
        return links;
    }

    public void setLinks(Set<String> links) {
        this.links = links;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public String getScreenShot() {
        return screenShot;
    }

    public void setScreenShot(String screenShot) {
        this.screenShot = screenShot;
    }

    @Override
    public String toString() {
        return "EmailDetails{" +
                "id='" + id + '\'' +
                ", subject='" + subject + '\'' +
                ", from='" + from + '\'' +
                ", time=" + time +
                ", to='" + to + '\'' +
                ", content='" + content + '\'' +
                ", links=" + links +
                ", html='" + html + '\'' +
                ", screenShot='" + screenShot + '\'' +
                '}';
    }
}
