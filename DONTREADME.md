## Goals

### Likelihood

> Are the likelihood scores guaranteed to match real-world frequencies?

No. The scores come from the model's distribution, not real-world counts. They're unlikely to match reality perfectly.

But they help favor natural phrasing over awkward or rare alternatives.

> Does `cue` use the mean log-likelihood to score sentences?

No. The score is the sum of the log-likelihoods.

A sentence's probability is the product of its token probabilities. In log space, that product becomes a sum. So the sum of log-likelihoods is the measure of the likelihood of the sentence.

The mean would measure the average probability per token. That's a different calculation that introduces an artificial bias by normalizing for length.

### Quantity

> How many sentences does the pipeline generate?

The goal is at least 1,000 sentences.

### Cost

> What is the budget for generating and ranking the sentences?

The goal is to stay under $1,000.

## Setup

> How do I set up the local development environment?

1. Install [devenv](https://github.com/cachix/devenv/blob/aa22abe84c3b8f7b0a40b7aac018c080ad8bac14/docs/src/getting-started.md#installation).

1. Install [direnv](https://github.com/cachix/devenv/blob/aa22abe84c3b8f7b0a40b7aac018c080ad8bac14/docs/src/integrations/direnv.md#installing-direnv).

1. Run the following commands:

   ```sh
   git clone git@github.com:8ta4/cue.git
   cd cue
   direnv allow
   ```

The `devenv.nix` file has got all the scripts you need.

> How do I set up the remote development environment?

1. Install [devenv](https://github.com/cachix/devenv/blob/aa22abe84c3b8f7b0a40b7aac018c080ad8bac14/docs/src/getting-started.md#installation) on your local machine.

1. Install [direnv](https://github.com/cachix/devenv/blob/aa22abe84c3b8f7b0a40b7aac018c080ad8bac14/docs/src/integrations/direnv.md#installing-direnv) on your local machine.

1. Run the following commands on your local machine:

   ```sh
   git clone git@github.com:8ta4/cue.git
   cd cue
   direnv allow
   ```

1. Make sure `ssh cue` logs you in to your remote machine.

1. Install devenv on your remote machine.

1. Install direnv on your remote machine.

1. Run the following commands on your remote machine:

   ```sh
   cd
   git clone https://github.com/8ta4/cue
   cd cue
   direnv allow
   ```

Now you can start the remote nREPL and port forwarding by running `devenv up` on your local machine.

## Search

> How do I run a search?

You run a search with the `candidates` command.

```sh
clj -M:prod -m candidates
```

This finds sentences and saves them to `data/candidates.ednl`.

> Does `cue` parse sentences from a corpus?

No. There are issues with conversational datasets:

- Most datasets are too small. In a limited sample, common patterns are easily missed while outliers may be overrepresented.

- The settings are often artificial. SWITCHBOARD, for instance, features strangers who were told to discuss assigned topics over the phone.

Instead, `cue` mines the model's probability distribution.

> Does `cue` use an instruction-tuned model to find candidates?

No. Instruction-tuned models are fine-tuned to be helpful. That fine-tuning warps their probability distribution toward how an assistant should act rather than how humans talk.

Instead, `cue` uses a base model.

> Does `cue` fine-tune a base model?

No. Spontaneous conversation data is hard to find. Fine-tuning on artificially constructed data risks biasing the model toward the patterns of forced settings.

> What model does `cue` use to find and rank sentences?

`cue` uses [`Qwen3-30B-A3B-Base`](https://huggingface.co/Qwen/Qwen3-30B-A3B-Base). Its instruction-tuned counterpart, [`Qwen3-30B-A3B-Instruct-2507`](https://huggingface.co/Qwen/Qwen3-30B-A3B-Instruct-2507), is the highest-ranking model with fewer than 100B parameters on [LMArena's Text Arena](https://lmarena.ai/leaderboard/text). I use the rank of the instruction-tuned version as a proxy to judge the quality of the base model. Its low VRAM footprint lets me run it at full precision on a single GPU.

It also helps that the same family includes smaller models. I can iterate quickly on my development machine without worrying about inconsistencies when I move to production. But for the production run, slow and steady wins the base.

> Does `cue` use a base model with a prompt?

Yes. It uses the prompt `She's like, "`. There are a few reasons:

- Women outnumber men in the US, making `she` a more sensible default than `he`.

- The phrasing `She's like` primes the model for a more casual completion than `She says`.

- The opening quote primes the model for spoken dialogue.

> Does `cue` use multiple prompts to produce a dataset?

No. Combining samples from different contexts would likely lead to an unrepresentative distribution since the real world frequency of each context is unknown.

> Are the sentences guaranteed to be grammatical?

No. People often speak ungrammatically. The goal is to capture the sentences that people likely ask in conversation.

> Does an LLM as a judge assign the likelihood scores?

No. Using an LLM as a judge to rank the list isn't straightforward. A full pairwise comparison would be quadratic and expensive. A faster method like Elo has its own problems, like figuring out when the ratings have stabilized.

The likelihood scores are the log probabilities a model calculates during search.

> Can a sentence found by `cue` contain a double quote?

No. That's the one character the pipeline prunes. Since the prompt `She's like, "` uses double quotes to frame the dialogue, another double quote inside the completion would create malformed output.

> Does `cue` only find questions?

No. A question demands an answer, which creates a clear setup. But statements are cues, too. They're great for practicing how to react and build on a conversation.

If you just want to drill questions, you can filter for a question mark in a spreadsheet.

> What marks the end of a sentence found by `cue`?

The search terminates on the first occurrence of a period (`.`), question mark (`?`), or exclamation mark (`!`).

> Can stopping on the first period create sentence fragments?

Yes. If fragments show up, they can be fixed. The fix would involve running a targeted search starting from each fragment.

> Can a sentence found by `cue` end with an ellipsis?

Yes. How the pipeline handles an ellipsis depends on which of its two forms appears:

- The three-dot ellipsis (`...`) can't be formed. The rule to stop on the first period prevents that sequence from coming together.

- The single-character ellipsis (`…`) can be found. It'll likely lose to a version that ends in a period during deduplication.

> Does `cue` filter out sentences that don't seem to stand on their own?

No. The goal is a realistic training set. Conversation is full of sentences that refer back to the previous turn.

> Does `cue` cap the length of a candidate during search?

Yes. During search, `cue` stops expanding a candidate once its completion reaches 100 tokens.

The cap is there to keep pathological repetition from consuming unbounded memory and compute.

The kind of conversational one-sentence cues `cue` is trying to collect probably won't get anywhere near 100 tokens anyway.

> Can a search run finish on its own?

Yes. The search is guided by a log-likelihood threshold. This threshold is the minimum score a sentence needs from the model to make it into the results.

The formula is:

$$
P(\text{completion} | \text{prompt}) \ge e^{\text{threshold}}
$$

A run finishes on its own once it's found every sentence that makes the cut.

If you stop the run early, you can still see how far the search got. While it's running, `cue` keeps writing a single number to `data/guarantee`. This number is the guarantee.

Any completion with a higher score than the guarantee has already been considered:

$$
P(\text{completion} | \text{prompt}) \ge e^{\text{guarantee}}
$$

## Cleanup

> How do I clean up candidates?

You clean up the candidates with the `cues` command.

```sh
clj -M -m cues
```

This trims the candidates, filters the results, deduplicates the rest, ranks them, and saves them to `data/cues.csv`.

I use CSV because the final output is meant for humans to read in a spreadsheet.

> Does `cue` trim leading whitespace from candidates?

Yes. `cue` removes any leading whitespace from each candidate.

> When `cue` trims candidates, does it drop everything after the first period?

Yes. When `cue` trims candidates, it drops everything after the first period. If a question mark or an exclamation mark comes first instead, it drops everything after that mark instead.

> Does `cue` filter out punctuation-only sentences?

Yes. `cue` filters out sentences whose trimmed form contains no Unicode letters or digits.

> Does `cue` use semantic similarity to deduplicate sentences?

No. `cue` doesn't use semantic similarity to deduplicate sentences. Two sentences can be similar in meaning and still differ in wording, and that difference in wording can change how you respond. So `cue` only deduplicates sentences that normalize to the same form.

> Does `cue` deduplicate two sentences that differ only in capitalization?

Yes. For deduplication, `cue` compares sentences by a normalized form. To build that form, `cue` casefolds each sentence, applies Unicode compatibility decomposition, and removes every character that is not a Unicode letter or digit.

> Does `cue` use canonical decomposition?

No. For deduplication, `cue` uses compatibility decomposition instead of canonical decomposition.

That way, typographically different versions of the same wording get treated as the same sentence instead of being kept separate.

> What does `cue` rank the sentences by?

`cue` ranks the sentences by likelihood score.
