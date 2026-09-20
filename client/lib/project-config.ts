export const PROJECT_CONFIG = {
    name: "GitBot",
    tagline: "AI-Powered Codebase Assistant",
    description:
        "Connect GitHub, index any repository, and chat with your codebase using retrieval-augmented answers and citations.",
    portfolioNote:
        "GitBot is an open-source portfolio project. Watch the demo, explore the implementation, and view the source on GitHub.",
    developer: {
        name: "Manit Saxena",
        role: "Developer / Software Engineer",
        githubUrl: "https://github.com/ManitGitUser",
        email: "manithumain@gmail.com",
    },
    // The GitBot repository URL can be updated here once the final URL is confirmed
    repositoryUrl: "https://github.com/ManitGitUser/GitBot",
    // Demo video / documentation reference on the repository README
    demoUrl: "https://github.com/ManitGitUser/GitBot#demo",
} as const;

export function getBugReportMailto(): string {
    const subject = "GitBot Bug Report";
    const body = `Hi Manit,

I found a bug in GitBot.

What happened:
[Please describe the issue]

Steps to reproduce:
[Please provide the steps]

Expected behavior:
[What should have happened?]

Browser/device:
[Optional]

GitBot page:
[Optional]

Thanks.`;

    const params = new URLSearchParams({
        subject,
        body,
    });

    return `mailto:${PROJECT_CONFIG.developer.email}?${params.toString().replace(/\+/g, "%20")}`;
}

