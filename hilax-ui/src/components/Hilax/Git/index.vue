<template>
  <div>
    <svg-icon icon-class="github" @click="goto" />
  </div>
</template>

<script>
export default {
  name: 'HilaxGit',
  data() {
    return {
      url: 'https://hilax.local/hilax'
    }
  },
  methods: {
    goto() {
      window.open(this.url)
    }
  }
}
</script>