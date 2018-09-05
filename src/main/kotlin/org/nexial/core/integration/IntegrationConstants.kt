package org.nexial.core.integration

import org.nexial.core.NexialConst

const val INTEGRATION = NexialConst.NAMESPACE + "integration"
// jira actions

// comment
const val JIRA_COMMENT_BODY = "jiraCommentBody"

// defect
const val JIRA_PROJECT_KEY = "jiraProjectKey"
const val JIRA_DEFECT_SUMMARY = "jiraDefectSummary"
const val JIRA_DEFECT_DESCRIPTION = "jiraDefectDescription"

// label
const val JIRA_LABELS = "jiraLabels"

const val DEFECT_LABEL = "NEXIAL_DEFECT"
const val AUTOMATION_COMPLETE = "NEXIAL_AUTOMATION_COMPLETE"

// link
const val JIRA_INWARD_ISSUE = "jiraInwardIssue"
const val JIRA_OUTWARD_ISSUE = "jiraOutwardIssue"
const val JIRA_ISSUE_LINK_URL = "issueLinkUrl"

// slack actions
// comment
const val SLACK_CHAT_URL = "chatUrl"
const val SLACK_COMMENT_BODY ="slackCommentBody"

// templates
const val COMMENT_ENDPOINT = "addComment"
const val DEFECT_ENDPOINT = "createDefect"
const val LABEL_ENDPOINT = "addLabels"
const val LINK_ENDPOINT = "addLink"
